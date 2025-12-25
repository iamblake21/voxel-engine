package engine.ui.widget;

import engine.ui.GuiComponent;
import engine.ui.GuiRenderer;
import engine.window.InputManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiScrollableList extends GuiComponent {

    private final List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0; // In pixels or item indices? Indices is easier.
    private int itemHeight = 40;

    private Consumer<Integer> onSelect;

    public GuiScrollableList(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setItems(List<String> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
        // Clamp selection/scroll
        if (selectedIndex >= this.items.size())
            selectedIndex = -1;
        scrollOffset = 0;
    }

    public void setOnSelect(Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
    }

    public String getSelectedItem() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            return items.get(selectedIndex);
        }
        return null;
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }

    public void input(InputManager input, double mx, double my, boolean mousePressed) {
        if (!visible)
            return;

        // Mouse Wheel
        double scrollY = input.getScrollY();
        if (scrollY != 0 && isMouseOver((int) mx, (int) my)) {
            scrollOffset -= (int) scrollY;
            // Clamp scroll
            int maxScroll = Math.max(0, items.size() - (height / itemHeight));
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }

        // Selection
        if (mousePressed) {
            if (isMouseOver((int) mx, (int) my)) {
                int relativeY = (int) my - y;
                int indexClicked = scrollOffset + (relativeY / itemHeight);

                if (indexClicked >= 0 && indexClicked < items.size()) {
                    selectedIndex = indexClicked;
                    if (onSelect != null) {
                        onSelect.accept(selectedIndex);
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // Background
        renderer.renderRect(x, y, width, height, 0.15f, 0.15f, 0.15f, 1.0f);

        // Items
        // Determine visible range
        int visibleCount = (height / itemHeight) + 1;

        for (int i = 0; i < visibleCount; i++) {
            int index = scrollOffset + i;
            if (index >= items.size())
                break;

            int itemY = y + (i * itemHeight);

            // Don't render if exceeds height (clipping logic manual)
            if (itemY + itemHeight > y + height)
                break;

            boolean isSelected = (index == selectedIndex);

            // Item Background
            float r = isSelected ? 0.3f : 0.2f;
            float g = isSelected ? 0.3f : 0.2f;
            float b = isSelected ? 0.5f : 0.2f;

            // Alternating colors slightly
            if (!isSelected && index % 2 == 1) {
                r += 0.02f;
                g += 0.02f;
                b += 0.02f;
            }

            renderer.renderRect(x + 2, itemY + 1, width - 4, itemHeight - 2, r, g, b, 1.0f);

            // Text
            String itemText = items.get(index);
            // Center text somewhat? Or left align.
            renderer.renderText(itemText, x + 10, itemY + 10, 2.5f, 1, 1, 1, 1);
        }

        // Scrollbar
        if (items.size() > 0) {
            int totalHeight = items.size() * itemHeight;
            if (totalHeight > height) {
                float scrollPercent = (float) scrollOffset / (float) (items.size() - visibleCount);
                if (Float.isNaN(scrollPercent))
                    scrollPercent = 0;
                scrollPercent = Math.max(0, Math.min(1, scrollPercent));

                float barHeight = Math.max(20, (float) height / (float) totalHeight * height);
                float barY = y + (height - barHeight) * scrollPercent;

                renderer.renderRect(x + width - 6, barY, 4, barHeight, 0.6f, 0.6f, 0.6f, 1.0f);
            }
        }

        // Border
        renderer.renderRect(x, y, width, 2, 0.4f, 0.4f, 0.4f, 1f); // T
        renderer.renderRect(x, y + height - 2, width, 2, 0.4f, 0.4f, 0.4f, 1f); // B
    }
}
