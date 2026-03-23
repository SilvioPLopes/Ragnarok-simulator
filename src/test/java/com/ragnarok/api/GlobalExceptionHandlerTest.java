package com.ragnarok.api;

import com.ragnarok.application.service.BattleService;
import com.ragnarok.api.controller.BattleController;
import com.ragnarok.api.dto.request.AttackRequestDTO;
import com.ragnarok.domain.exception.InsufficientSpException;
import com.ragnarok.domain.exception.PlayerDeadException;
import com.ragnarok.domain.exception.SkillNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BattleController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BattleService battleService;

    @Test
    void playerDeadException_returns400WithErrorField() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenThrow(new PlayerDeadException());

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void illegalArgumentException_returns404() throws Exception {
        when(battleService.realizarAtaque(99L, 2L))
                .thenThrow(new IllegalArgumentException("Player not found"));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(99L, 2L))))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("Player not found"));
    }

    @Test
    void skillNotFoundException_returns404() throws Exception {
        when(battleService.realizarAtaque(1L, 2L))
                .thenThrow(new SkillNotFoundException("SM_BASH"));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("Skill SM_BASH não encontrada."));
    }

    @Test
    void gameExceptionSubclass_returns400() throws Exception {
        when(battleService.realizarAtaque(1L, 2L)).thenThrow(new InsufficientSpException(20, 5));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unexpectedException_returns500() throws Exception {
        when(battleService.realizarAtaque(1L, 2L))
                .thenThrow(new RuntimeException("unexpected"));

        mvc.perform(post("/api/battle/attack")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new AttackRequestDTO(1L, 2L))))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.error").exists());
    }
}
