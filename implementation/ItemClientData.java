package [PACOTE_BASE].populator.dto;

import java.util.List;

public class ItemClientData {

    private final int itemId;
    private final String displayName;
    private final String resourceName;
    private final List<String> description;

    public ItemClientData(int itemId, String displayName,
                          String resourceName, List<String> description) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.resourceName = resourceName;
        this.description = description;
    }

    public int getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getResourceName() { return resourceName; }
    public List<String> getDescription() { return description; }
}