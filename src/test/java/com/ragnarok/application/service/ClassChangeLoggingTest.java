package com.ragnarok.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ragnarok.domain.exception.GameException;
import com.ragnarok.domain.model.JobClass;
import com.ragnarok.infrastructure.persistence.PlayerEntity;
import com.ragnarok.infrastructure.persistence.PlayerRepository;
import com.ragnarok.infrastructure.persistence.SkillTreeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassChangeLoggingTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private SkillTreeRepository skillTreeRepository;

    @InjectMocks
    private ClassChangeService classChangeService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachAppender() {
        Logger classLogger = (Logger) LoggerFactory.getLogger(ClassChangeService.class);
        // Força nível INFO para este teste — pode ter sido sobrescrito por SpringBootTest
        // anterior que carrega application-test.properties com logging.level.com.ragnarok=WARN
        classLogger.setLevel(Level.INFO);
        logAppender = new ListAppender<>();
        logAppender.start();
        classLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        Logger classLogger = (Logger) LoggerFactory.getLogger(ClassChangeService.class);
        classLogger.detachAppender(logAppender);
        classLogger.setLevel(null); // herda do pai novamente
    }

    @Test
    @DisplayName("Logging: deve emitir INFO ao trocar classe com sucesso de NOVICE para SWORDSMAN")
    void trocarClasse_deveEmitirInfoLog_quandoBemSucedido() {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setJobClass("NOVICE");
        player.setJobLevel(10);

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(skillTreeRepository.findDistinctJobClasses())
                .thenReturn(List.of("SWORDSMAN", "MAGE", "ARCHER", "ACOLYTE", "THIEF", "MERCHANT"));
        when(playerRepository.save(any())).thenReturn(player);

        classChangeService.trocarClasse(1L, JobClass.SWORDSMAN);

        List<ILoggingEvent> logs = logAppender.list;
        assertEquals(1, logs.size(), "Deve haver exatamente 1 evento de log");
        assertEquals(Level.INFO, logs.get(0).getLevel());

        String msg = logs.get(0).getFormattedMessage();
        assertTrue(msg.contains("1"),          "Log deve conter o ID do player");
        assertTrue(msg.contains("NOVICE"),     "Log deve mencionar a classe anterior");
        assertTrue(msg.contains("SWORDSMAN"),  "Log deve mencionar a nova classe");
    }

    @Test
    @DisplayName("Logging: não deve emitir INFO quando troca de classe falha por job level insuficiente")
    void trocarClasse_naoDeveEmitirLog_quandoJobLevelInsuficiente() {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setJobClass("NOVICE");
        player.setJobLevel(5); // Requer 9

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        lenient().when(skillTreeRepository.findDistinctJobClasses())
                .thenReturn(List.of("SWORDSMAN"));

        assertThrows(GameException.class,
                () -> classChangeService.trocarClasse(1L, JobClass.SWORDSMAN));

        assertTrue(logAppender.list.isEmpty(), "Falha na troca de classe não deve gerar log INFO");
    }
}
