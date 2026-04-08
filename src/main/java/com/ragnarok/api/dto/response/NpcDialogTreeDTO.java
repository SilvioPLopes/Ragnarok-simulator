package com.ragnarok.api.dto.response;

import com.ragnarok.domain.model.NpcDialogNode;
import java.util.List;

public record NpcDialogTreeDTO(List<NpcDialogNode> nodes) {}
