package dev.boredvico.pik.util;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class RedactedWhitelistComponent implements Component {
	private final Component wrapped;

	public RedactedWhitelistComponent(Component wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public String getString() {
	    return "not whitelisted";
	}

	@Override
	public Style getStyle() {
		return wrapped.getStyle();
	}

	@Override
	public ComponentContents getContents() {
		return wrapped.getContents();
	}

	@Override
	public List<Component> getSiblings() {
		return wrapped.getSiblings();
	}

	@Override
	public FormattedCharSequence getVisualOrderText() {
		return wrapped.getVisualOrderText();
	}

	@Override
	public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
		return wrapped.visit(consumer, style);
	}

	@Override
	public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
		return wrapped.visit(consumer);
	}
}
