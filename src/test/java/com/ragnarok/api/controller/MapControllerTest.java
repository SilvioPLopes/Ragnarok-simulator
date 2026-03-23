package com.ragnarok.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragnarok.api.GlobalExceptionHandler;
import com.ragnarok.api.dto.request.TravelRequestDTO;
import com.ragnarok.application.dto.WalkResult;
import com.ragnarok.application.service.MapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapController.class)
@Import(GlobalExceptionHandler.class)
class MapControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MapService mapService;

    @Test
    void getMapInfo_returnsCurrentMapAndPortals() throws Exception {
        when(mapService.getCurrentMap(1L)).thenReturn("prontera");
        when(mapService.getPortals("prontera")).thenReturn(List.of("izlude", "geffen"));

        mvc.perform(get("/api/players/1/map"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.currentMap").value("prontera"))
           .andExpect(jsonPath("$.availablePortals[0]").value("izlude"));
    }

    @Test
    void getPortals_returnsList() throws Exception {
        when(mapService.getPortals("prontera")).thenReturn(List.of("izlude", "geffen"));

        mvc.perform(get("/api/maps/prontera/portals"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0]").value("izlude"));
    }

    @Test
    void walk_encounterOccurred_returnsMonsterInfo() throws Exception {
        WalkResult walkResult = new WalkResult(true, 1002L, "Poring", 55, "PORING APARECEU!");
        when(mapService.walk(1L)).thenReturn(walkResult);

        mvc.perform(post("/api/players/1/map/walk"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.encounterOccurred").value(true))
           .andExpect(jsonPath("$.monsterName").value("Poring"));
    }

    @Test
    void walk_noEncounter_returnsEncounterFalse() throws Exception {
        WalkResult walkResult = new WalkResult(false, null, null, null, "Nenhum monstro.");
        when(mapService.walk(1L)).thenReturn(walkResult);

        mvc.perform(post("/api/players/1/map/walk"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.encounterOccurred").value(false));
    }

    @Test
    void travel_returnsOk() throws Exception {
        mvc.perform(post("/api/players/1/map/travel")
               .contentType(MediaType.APPLICATION_JSON)
               .content(objectMapper.writeValueAsString(new TravelRequestDTO("izlude"))))
           .andExpect(status().isOk());
    }
}
