package com.ragnarok.runner.importer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que erros de parsing emitem WARN via SLF4J
 * (e não System.err, que seria invisível nos logs do servidor).
 */
class ParserLoggingTest {

    private ListAppender<ILoggingEvent> itemLogAppender;
    private ListAppender<ILoggingEvent> mobLogAppender;

    private final ItemDbParser itemDbParser = new ItemDbParser();
    private final MobDbParser  mobDbParser  = new MobDbParser();

    @BeforeEach
    void attachAppenders() {
        Logger itemLogger = (Logger) LoggerFactory.getLogger(ItemDbParser.class);
        itemLogger.setLevel(Level.WARN);
        itemLogAppender = new ListAppender<>();
        itemLogAppender.start();
        itemLogger.addAppender(itemLogAppender);

        Logger mobLogger = (Logger) LoggerFactory.getLogger(MobDbParser.class);
        mobLogger.setLevel(Level.WARN);
        mobLogAppender = new ListAppender<>();
        mobLogAppender.start();
        mobLogger.addAppender(mobLogAppender);
    }

    @AfterEach
    void detachAppenders() {
        Logger itemLogger = (Logger) LoggerFactory.getLogger(ItemDbParser.class);
        itemLogger.detachAppender(itemLogAppender);
        itemLogger.setLevel(null);

        Logger mobLogger = (Logger) LoggerFactory.getLogger(MobDbParser.class);
        mobLogger.detachAppender(mobLogAppender);
        mobLogger.setLevel(null);
    }

    @Test
    @DisplayName("ItemDbParser: deve emitir WARN ao falhar parsing de item com campo Name inválido")
    void itemDbParser_deveEmitirWarnAoParsearItemInvalido() {
        // YAML onde Name é um inteiro — causa ClassCastException na linha (String) item.get("Name")
        String yamlInvalido = """
                Header:
                  Type: ITEM_DB
                Body:
                  - Id: 501
                    Name: 999
                    Type: Healing
                """;

        List<?> resultado = itemDbParser.parse(yamlInvalido);

        // O item inválido é filtrado (retorna null na exceção)
        assertTrue(resultado.isEmpty(), "Item inválido deve ser filtrado do resultado");

        // Deve ter emitido exatamente 1 WARN
        List<ILoggingEvent> logs = itemLogAppender.list;
        assertEquals(1, logs.size(), "Deve haver exatamente 1 log de aviso");
        assertEquals(Level.WARN, logs.get(0).getLevel());
        assertTrue(logs.get(0).getFormattedMessage().contains("Erro ao parsear item"),
                "Mensagem de log deve indicar o erro de parsing");
    }

    @Test
    @DisplayName("MobDbParser: deve emitir WARN ao falhar parsing de monstro com campo Name inválido")
    void mobDbParser_deveEmitirWarnAoParsearMonstroInvalido() {
        // YAML onde Name é um inteiro — causa ClassCastException na linha (String) mob.get("Name")
        String yamlInvalido = """
                Header:
                  Type: MOB_DB
                Body:
                  - Id: 1002
                    Name: 42
                    Hp: 100
                """;

        List<?> resultado = mobDbParser.parse(yamlInvalido);

        assertTrue(resultado.isEmpty(), "Monstro inválido deve ser filtrado do resultado");

        List<ILoggingEvent> logs = mobLogAppender.list;
        assertEquals(1, logs.size(), "Deve haver exatamente 1 log de aviso");
        assertEquals(Level.WARN, logs.get(0).getLevel());
        assertTrue(logs.get(0).getFormattedMessage().contains("Erro ao parsear monstro"),
                "Mensagem de log deve indicar o erro de parsing");
    }

    @Test
    @DisplayName("ItemDbParser: itens válidos não devem gerar nenhum log de erro")
    void itemDbParser_itemValido_naoDeveGerarLog() {
        String yamlValido = """
                Header:
                  Type: ITEM_DB
                Body:
                  - Id: 501
                    Name: Red_Potion
                    Type: Healing
                    Buy: 50
                    Weight: 70
                """;

        List<?> resultado = itemDbParser.parse(yamlValido);

        assertFalse(resultado.isEmpty());
        assertTrue(itemLogAppender.list.isEmpty(), "Itens válidos não devem gerar nenhum log de aviso");
    }

    @Test
    @DisplayName("MobDbParser: monstros válidos não devem gerar nenhum log de erro")
    void mobDbParser_monstroValido_naoDeveGerarLog() {
        String yamlValido = """
                Header:
                  Type: MOB_DB
                Body:
                  - Id: 1002
                    Name: Poring
                    Hp: 50
                    Attack: 7
                    Defense: 5
                """;

        List<?> resultado = mobDbParser.parse(yamlValido);

        assertFalse(resultado.isEmpty());
        assertTrue(mobLogAppender.list.isEmpty(), "Monstros válidos não devem gerar nenhum log de aviso");
    }
}
