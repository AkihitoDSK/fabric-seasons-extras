package io.github.lucaargolo.seasonsextras.mixin;

import io.github.lucaargolo.seasonsextras.mixed.FontManagerMixed;
import io.github.lucaargolo.seasonsextras.utils.StyledTextRenderer;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(FontManager.class)
public class FontManagerMixin implements FontManagerMixed {

    @Shadow @Final Map<Identifier, FontStorage> fontStorages;

    @Shadow private Map<Identifier, Identifier> idOverrides;

    @Shadow @Final private FontStorage missingStorage;

    @Override
    public TextRenderer createStyledTextRenderer(Style style) {
        return new StyledTextRenderer((id) -> {
            return this.fontStorages.getOrDefault(this.idOverrides.getOrDefault(id, id), this.missingStorage);
        }, false, style);
    }


}
