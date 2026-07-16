package jp.ac.nitech.cc.hop.assistant.constructor;

import org.jetbrains.annotations.NotNull;

/** TODO {@link #fixed}ではない{@link #from}の拡張クラスを見つけた場合に、{@link #fixed}に揃える動作を実装したい */
public class Expand {
	/** 確定クラス */
	@NotNull
	public final String	fixed;
	/** 拡張可能クラス */
	public final String	from;
	Expand(@NotNull final String fixed, final String from) {
		this.fixed	= fixed;
		this.from	= from;
	}
	public String toString() {
		return fixed;
	}
}