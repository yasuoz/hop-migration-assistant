package jp.ac.nitech.cc.hop.assistant;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * IntelliJ公式テンプレート標準の多言語化(i18n)ヘルパークラスのJava実装です。
 * `src/main/resources/messages/I18n.properties` から文字を自動抽出します。
 */
public final class I18n extends DynamicBundle {
	private static final	String		BUNDLE_NAME = "messages.I18n";

	// シングルトンではなく、静的ユーティリティメソッドとして公開するため
	// 唯一のインスタンスを内部で保持
	private static final I18n INSTANCE = new I18n();

	private I18n() {
		super(BUNDLE_NAME);
	}

	@Nls
	public static String message(
			@PropertyKey(resourceBundle = BUNDLE_NAME) final String key,
			final Object... params
	) {
		return INSTANCE.getMessage(key, params);
	}

	@Nls
	public static Supplier<String> messagePointer(
			@PropertyKey(resourceBundle = BUNDLE_NAME) final String key,
			final Object... params
	) {
		return INSTANCE.getLazyMessage(key, params);
	}
}