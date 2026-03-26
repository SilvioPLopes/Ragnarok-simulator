package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.GlobalExceptionHandler;
import com.ragnarok.api.dto.request.UseSkillRequestDTO;
import com.ragnarok.application.dto.SkillRowDTO;
import com.ragnarok.application.service.AccountService;
import com.ragnarok.application.service.SkillCombatService;
import com.ragnarok.application.service.SkillService;
import com.ragnarok.domain.exception.InsufficientSpException;
import com.ragnarok.infrastructure.security.JwtFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillController.class)
@Import(GlobalExceptionHandler.class)
class SkillControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SkillService skillService;
    @MockBean SkillCombatService skillCombatService;
    @MockBean AccountService accountService;
    @MockBean JwtFilter jwtFilter;

    @BeforeEach
    void passFilterThrough() throws Exception {
        doAnswer(inv -> { ((jakarta.servlet.FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void listSkills_returnsList() throws Exception {
        when(skillService.listarSkillsDoPlayer(1L)).thenReturn(
                List.of(new SkillRowDTO("SM_BASH", "Bash", 10, 1, true, null)));

        mvc.perform(get("/api/players/1/skills"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].aegisName").value("SM_BASH"));
    }

    @Test
    void learnSkill_returnsMessage() throws Exception {
        when(skillService.aprenderSkill(1L, "SM_BASH")).thenReturn("SM_BASH agora está no nível 2");

        mvc.perform(post("/api/players/1/skills/SM_BASH/learn"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("SM_BASH agora está no nível 2"));
    }

    @Test
    void useSkill_insufficientSp_returns400() throws Exception {
        when(skillCombatService.usarSkillEmCombate(1L, "SM_BASH", 2L))
                .thenThrow(new InsufficientSpException(20, 5));

        mvc.perform(post("/api/players/1/skills/SM_BASH/use")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new UseSkillRequestDTO(2L))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }
}
