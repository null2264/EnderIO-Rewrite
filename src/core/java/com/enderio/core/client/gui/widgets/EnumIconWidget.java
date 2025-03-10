package com.enderio.core.client.gui.widgets;

import com.enderio.core.client.gui.IIcon;
import com.enderio.core.client.gui.screen.IEnderScreen;
import com.enderio.core.client.gui.screen.IFullScreenListener;
import com.enderio.core.common.util.Vector2i;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnumIconWidget<T extends Enum<T> & IIcon, U extends Screen & IEnderScreen> extends AbstractWidget implements IFullScreenListener {

    private final Supplier<T> getter;
    private final Consumer<T> setter;

    private final Map<T, SelectionWidget> icons = new HashMap<>();

    private final Vector2i expandTopLeft, expandBottomRight;

    private static final int ELEMENTS_IN_ROW = 5;
    private static final int SPACE_BETWEEN_ELEMENTS = 4;

    private boolean isExpanded = false;

    private int mouseButton = 0;

    private final U addedOn;

    private final SelectionScreen selection;

    // TODO: I don't like that this is separate, maybe we need an IOptionIcon for holding the option name?
    private final Component optionName;

    public EnumIconWidget(U addedOn, int pX, int pY, Supplier<T> getter, Consumer<T> setter, Component optionName) {
        super(pX, pY, getter.get().getRenderSize().x(), getter.get().getRenderSize().y(), Component.empty());
        this.getter = getter;
        this.setter = setter;
        this.optionName = optionName;
        T[] values = getter.get().getDeclaringClass().getEnumConstants();
        Vector2i pos = calculateFirstPosition(values[0], values.length);
        Vector2i elementDistance = values[0].getRenderSize().expand(SPACE_BETWEEN_ELEMENTS);
        for (int i = 0; i < values.length; i++) {
            T value = values[i];
            Vector2i subWidgetPos = pos.add(getColumn(i) * elementDistance.x(), getRow(i)* elementDistance.y()).add(pX, pY);
            SelectionWidget widget = new SelectionWidget(subWidgetPos, value);
            icons.put(value, widget);
        }

        Vector2i topLeft = Vector2i.MAX;
        Vector2i bottomRight = Vector2i.MIN;
        for (SelectionWidget widget : icons.values()) {
            topLeft = topLeft.withX(Math.min(topLeft.x(), widget.x));
            topLeft = topLeft.withY(Math.min(topLeft.y(), widget.y));
            bottomRight = bottomRight.withX(Math.max(bottomRight.x(), widget.x + widget.getWidth()));
            bottomRight = bottomRight.withY(Math.max(bottomRight.y(), widget.y + widget.getHeight()));
        }
        expandTopLeft = topLeft.expand(-SPACE_BETWEEN_ELEMENTS);
        expandBottomRight = bottomRight.expand(SPACE_BETWEEN_ELEMENTS);
        this.addedOn = addedOn;
        this.selection = new SelectionScreen();
    }

    private Vector2i calculateFirstPosition(T icon, int amount) {
        int maxColumns = Math.min(amount, ELEMENTS_IN_ROW);
        int width = (maxColumns-1)*(icon.getRenderSize().x() + SPACE_BETWEEN_ELEMENTS);
        return new Vector2i(-width/2, 2 * SPACE_BETWEEN_ELEMENTS + icon.getRenderSize().y());
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        mouseButton = pButton;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected boolean isValidClickButton(int pButton) {
        return pButton == InputConstants.MOUSE_BUTTON_LEFT || pButton == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        if (isExpanded) {
            selectNext(mouseButton != InputConstants.MOUSE_BUTTON_RIGHT);
        } else {
            isExpanded = true;
            Minecraft.getInstance().pushGuiLayer(selection);
        }
    }

    private void selectNext(boolean isForward) {
        T[] values = getter.get().getDeclaringClass().getEnumConstants();
        int index = getter.get().ordinal() + (isForward ? 1 : -1) + values.length;
        setter.accept(values[index%values.length]);
    }

    private static int getColumn(int index) {
        return index%ELEMENTS_IN_ROW;
    }

    private static int getRow(int index) {
        return index / ELEMENTS_IN_ROW;
    }

    @Override
    public void renderButton(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTicks) {
        T icon = getter.get();
        addedOn.renderIconBackground(pPoseStack, new Vector2i(x, y), icon);
        IEnderScreen.renderIcon(pPoseStack, new Vector2i(x, y).expand(1), icon);
        renderToolTip(pPoseStack, pMouseX, pMouseY);
    }

    @Override
    public void renderToolTip(PoseStack poseStack, int mouseX, int mouseY) {
        if (isHovered && isActive()) {
            addedOn.renderTooltip(poseStack, List.of(optionName, getter.get().getTooltip().copy().withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput pNarrationElementOutput) {}

    @Override
    public void onGlobalClick(double mouseX, double mouseY) {
        if (isExpanded &&
            !(expandTopLeft.x() <= mouseX && expandBottomRight.x() >= mouseX
            && expandTopLeft.y() <= mouseY && expandBottomRight.y() >= mouseY
            || isMouseOver(mouseX, mouseY))) {
            isExpanded = false;
            Minecraft.getInstance().popGuiLayer();
        }
    }

    private class SelectionScreen extends Screen implements IEnderScreen {

        private final List<LateTooltipData> tooltips = new ArrayList<>();
        protected SelectionScreen() {
            super(Component.empty());
        }

        @Override
        protected void init() {
            addRenderableWidget(EnumIconWidget.this);
            EnumIconWidget.this.icons.values().forEach(this::addRenderableWidget);
        }

        @Override
        public void render(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTicks) {
            RenderSystem.disableDepthTest();
            tooltips.clear();
            renderSimpleArea(pPoseStack, expandTopLeft, expandBottomRight);
            super.render(pPoseStack, pMouseX, pMouseY, pPartialTicks);

            for (LateTooltipData tooltip : tooltips) {
                renderTooltip(tooltip.getPoseStack(), tooltip.getText(), tooltip.getMouseX(), tooltip.getMouseY());
            }
            RenderSystem.enableDepthTest();
        }

        @Override
        public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
            for (GuiEventListener widget: children()) {
                if (widget instanceof AbstractWidget abstractWidget && abstractWidget.isActive() && widget instanceof IFullScreenListener fullScreenListener) {
                    fullScreenListener.onGlobalClick(pMouseX, pMouseY);
                }
            }
            return super.mouseClicked(pMouseX, pMouseY, pButton);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void addTooltip(LateTooltipData data) {
            tooltips.add(data);
        }
    }

    private class SelectionWidget extends AbstractWidget {

        private final T value;

        public SelectionWidget(Vector2i pos, T value) {
            super(pos.x(), pos.y(), value.getRenderSize().x() + 2, value.getRenderSize().y() + 2, value.getTooltip());
            this.value = value;
        }

        @Override
        public void onClick(double pMouseX, double pMouseY) {
            super.onClick(pMouseX, pMouseY);
            setter.accept(value);
        }

        @Override
        public void updateNarration(NarrationElementOutput pNarrationElementOutput) {}

        @Override
        public void renderButton(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTicks) {
            if (getter.get() != value) {
                selection.renderIconBackground(pPoseStack, new Vector2i(x, y), value);
            } else {
                GuiComponent.fill(pPoseStack, x, y, x + width, y + height, 0xFF0020FF); //TODO: Client Config
                GuiComponent.fill(pPoseStack, x + 1, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B);
            }
            IEnderScreen.renderIcon(pPoseStack, new Vector2i(x, y).expand(1), value);

            if (isMouseOver(pMouseX, pMouseY)) {
                Component tooltip = value.getTooltip();
                if (tooltip != Component.empty()) {
                    selection.renderTooltipAfterEverything(pPoseStack, tooltip, pMouseX, pMouseY);
                }
            }
        }
    }
}
