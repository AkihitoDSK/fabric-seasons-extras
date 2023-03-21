package io.github.lucaargolo.seasonsextras.patchouli;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.lucaargolo.seasonsextras.FabricSeasonsExtrasClient;
import io.github.lucaargolo.seasonsextras.utils.ModIdentifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import vazkii.patchouli.client.book.gui.BookTextRenderer;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.page.PageText;
import vazkii.patchouli.client.book.text.Span;
import vazkii.patchouli.client.book.text.TextLayouter;
import vazkii.patchouli.common.base.PatchouliConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PageBiomeSearch extends PageText {

    private static final Identifier PATCHOULI_EXTRAS = new ModIdentifier("textures/gui/patchouli_extras.png");


    private final transient List<Pair<Identifier, String>> biomes = new ArrayList<>();
    private transient BookTextRenderer textRender;
    private transient TextFieldWidget searchBar;

    //Variables used for the scroll logic
    private double scrollableOffset = 0.0;
    private boolean scrollable = false;
    private double excessHeight = 0.0;
    private boolean draggingScroll = false;

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float pticks) {
        super.render(ms, mouseX, mouseY, pticks);
        textRender.render(ms, mouseX, mouseY);
        RenderSystem.setShaderTexture(0, PATCHOULI_EXTRAS);
        if(scrollable) {
            int mx = mouseX-parent.bookLeft;
            int my = mouseY-parent.bookTop;
            double offset = MathHelper.lerp(scrollableOffset/excessHeight, 12, 104);
            DrawableHelper.drawTexture(ms, 99, 11, 0f, 56f, 8, 115, 256, 256);
            DrawableHelper.drawTexture(ms, 100, (int) offset, 8f, 56f, 6, 21, 256, 256);
            if(mx > 100 && mx < 106 && my > offset && my < offset+21) {
                DrawableHelper.fill(ms, 100, (int) offset, 106, (int) offset + 21, -2130706433);
            }
        }
    }

    @Override
    public void onDisplayed(GuiBookEntry parent, int left, int top) {
        super.onDisplayed(parent, left, top);
        biomes.clear();
        FabricSeasonsExtrasClient.validBiomes.forEach(entry -> {
            entry.getKey().ifPresent(key -> {
                Identifier biomeId = key.getValue();
                String biomeName = Text.translatable(biomeId.toTranslationKey("biome")).getString();
                biomes.add(new Pair<>(biomeId, biomeName));
            });
        });
        textRender = new BookTextRenderer(parent, text.as(Text.class), 0, 12);
        int textSize = (int) (fontRenderer.getWidth("Search:")*0.8);
        searchBar = new TextFieldWidget(fontRenderer, parent.bookLeft+left+textSize, parent.bookTop+top+138, 105-textSize, 10, Text.literal(""));
        searchBar.setChangedListener(this::updateText);
        parent.addDrawableChild(searchBar);
        updateText(searchBar.getText());
    }

    public void updateText(String search) {
        scrollableOffset = 0.0;
        excessHeight = 0.0;
        scrollable = false;

        AtomicReference<String> string = new AtomicReference<>("");
        AtomicInteger height = new AtomicInteger(0);
        AtomicInteger overflow = new AtomicInteger(0);
        biomes.stream().filter(p -> p.getRight().toLowerCase().contains(search.toLowerCase())).forEach(p -> {
            if(height.getAndIncrement() < 13) {
                string.getAndUpdate(t -> t + "$(l:biomes#"+p.getLeft()+")"+p.getRight()+"$(br)$()");
            }else{
                scrollable = true;
                overflow.getAndIncrement();
            }
        });
        excessHeight = overflow.get()*GuiBook.TEXT_LINE_HEIGHT;
        while (height.getAndIncrement() < 13) {
            string.getAndUpdate(t -> t + "$(br)");
        }
        string.getAndUpdate(t -> t + "$(br)Search: ");
        setText(string.get());
        textRender = new BookTextRenderer(parent, text.as(Text.class), 0, getTextHeight());
    }

    public void updateTextHeight() {
        String search = searchBar.getText();
        int cycle = MathHelper.floor(scrollableOffset/GuiBook.TEXT_LINE_HEIGHT);
        if(cycle < biomes.size()) {
            AtomicReference<String> string = new AtomicReference<>("");
            AtomicInteger height = new AtomicInteger(0);
            biomes.subList(cycle, biomes.size()).stream().filter(p -> p.getRight().toLowerCase().contains(search.toLowerCase())).forEach(p -> {
                if(height.getAndIncrement() < 13) {
                    string.getAndUpdate(t -> t + "$(l:biomes#"+p.getLeft()+")"+p.getRight()+"$(br)$()");
                }
            });
            while (height.getAndIncrement() < 13) {
                string.getAndUpdate(t -> t + "$(br)");
            }
            string.getAndUpdate(t -> t + "$(br)Search: ");
            setText(string.get());
            textRender = new BookTextRenderer(parent, text.as(Text.class), 0, getTextHeight());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        int mx = (int) mouseX-parent.bookLeft;
        int my = (int) mouseY-parent.bookTop;
        if(scrollable && mx > 100 && mx < 106 && my > 12 && my < 125) {
            double offset = MathHelper.lerp(scrollableOffset/excessHeight, 12, 104);
            if(my > offset && my < offset+21) {
                draggingScroll = true;
            }else{
                scrollableOffset = MathHelper.lerp((my-12.0)/113.0, 0.0, excessHeight);
                updateTextHeight();
            }
        }
        return textRender.click(mouseX, mouseY, mouseButton);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if(draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double deltaX, double deltaY) {
        if(draggingScroll) {
            if(scrollable) {
                scrollableOffset += deltaY * (excessHeight/104);
                scrollableOffset = MathHelper.clamp(scrollableOffset, 0.0, excessHeight);
                updateTextHeight();
                return true;
            }else{
                draggingScroll = false;
                return false;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if(scrollable) {
            scrollableOffset -= amount*4;
            scrollableOffset = MathHelper.clamp(scrollableOffset, 0.0, excessHeight);
            updateTextHeight();
            return true;
        }
        return false;
    }

    public void tick() {
        searchBar.tick();
    }

    @Override
    public void onHidden(GuiBookEntry parent) {
        super.onHidden(parent);
        parent.removeDrawablesIf(d -> d.equals(searchBar));
        searchBar = null;
    }

    @Override
    public boolean shouldRenderText() {
        return false;
    }

}