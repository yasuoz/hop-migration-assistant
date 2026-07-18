package jp.ac.nitech.cc.hop.assistant.edit;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/** ツリーを書き換えず、文字列で最後に一挙に書き換えるための仕組み */
public abstract class EditText implements Comparable<EditText> {
	public final int	oldStart, oldEnd;
	EditText(final int oldStart, final int oldEnd) {
		this.oldStart	= oldStart;
		this.oldEnd		= oldEnd;
	}
	/** {@param buf}に中身を追記 */
	abstract void append(StringBuilder buf);
	/** 改修することで増える文字数 */
	abstract int increment();

	@Override
	public int compareTo(@NotNull EditText o) {
		final int cmpStart	= Integer.compare(oldStart, o.oldStart);
		return cmpStart != 0 ? cmpStart : Integer.compare(oldEnd, o.oldEnd);
	}
	public static class Insert extends EditText {
		final ArrayList<String>	inserts	= new ArrayList<>();
		Insert(final int oldStart) {
			super(oldStart, oldStart);
		}
		public void add(final String str) {
			inserts.add(str);
		}

		@Override
		void append(final StringBuilder buf) {
			for (final String s : inserts)	buf.append(s);
		}

		@Override
		int increment() {
			int	size	= 0;
			for (final String s : inserts)	size	+= s.length();
			return size;
		}
	}
	public static class Replace extends EditText {
		/** -1の値を持つ場合、{@link #replace}全部を引用 */
		public final int	newStart, newEnd;
		/** 置き換え予定の文字列 */
		@NotNull
		public final String	replace;
		Replace(final int oldStart, final int oldEnd, @NotNull final String replace, final int newStart, final int newEnd) {
			super(oldStart, oldEnd);
			this.replace	= replace;
			this.newStart	= newStart;
			this.newEnd		= newEnd;
		}

		@Override
		void append(final StringBuilder buf) {
			if (newStart >= 0) {
				buf.append(replace, newStart, newEnd);
			} else {
				buf.append(replace);
			}
		}

		@Override
		int increment() {
			final int	len	= newStart < 0 ? replace.length() : newEnd - newStart;
			return len + oldStart - oldEnd;
		}

		@Override
		@NotNull
		public String toString() {
			return newStart >= 0 ? replace.substring(newStart, newEnd) : replace;
		}
	}
}