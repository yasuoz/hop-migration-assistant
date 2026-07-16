package jp.ac.nitech.cc.hop.assistant.imports;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PentahoからHopに主要クラスを書き換え */
public class Converter {
	public static final String		EMPTY_STRING	= ""
			,						DOT				= ".";
	/** 変換対象外 */
	private static final Converter	EMPTY			= new Converter();
	/**
	 * Trans	→ Pipeline<br/>
	 * Step		→ Transform<br/>
	 * Job		→ Workflow<br/>
	 * JobEntry	→ Action<br/>
	 */
	private static final Pattern	HOP_MIGRATION_PATTERN	= Pattern.compile(
			"^(?:(\\s*import\\s+(static\\s+)?)?org.pentaho.di((?:\\.[_a-z0-9]+)+))?(?:(?:^|([:.]))((Kettle)|(?:(?!Job|Row|Step|Trans|V(?:ariable|FS)|XML)[A-Z][a-z0-9]*?)?)(Job(?:Entry)?|Step|Trans|VFS|XML)?((?:(?!Interface|Listener|RowSet|Variable)[A-Z][a-z]*)+?)?(Interface|Listener|RowSet|VariableSpace)?((?:\\.\\w+)*)?)?(\\.\\*)?(?=\\s*;?$)"
	)		,						PACKAGE_PATTERN			= Pattern.compile("(?<=^|\\.)(?:entr(?:ies|y)|job|repository|shared|step|trans)(?=\\.|$)");
	private final String			oldImport;
	private final StringBuilder		newImport;
	/** 最後の.位置 */
	private final int				oldStart, oldEnd, newStart, newEnd;
	/** 構造が変わらず */
	public final boolean			stay;
	/** 最後の個別パートが変わった */
	public final boolean			renamed;
	public final boolean			isStatic, isWildCard;
	private String					oldName, newName;
	/** @param	oldImport	変換対象のimport文あるいは完全修飾子付きクラス名(パッケージ名とクラス名の間は.と:いずれでも良い)
	 *  @param	start		{@param oldImport}の文字列の中でクラス名が始まるIndex
	 *  @param	end			{@param oldImport}の文字列の中でクラス名が終わるIndex
	 * */
	public static @NotNull Converter from(final StringBuilder buf, final String oldImport, final int start, final int end) {
		final Matcher	matcher;
		if (oldImport != null) {
			matcher	= HOP_MIGRATION_PATTERN.matcher(oldImport).region(start, end);
			if (matcher.find()) return new Converter(buf, oldImport, matcher);
		}
		return EMPTY;
	}
	public static @NotNull Converter from(final String oldImport) {
		return from(new StringBuilder(), oldImport, 0, oldImport != null ? oldImport.length() : 0);
	}
	/** 変換対象外 */
	private Converter() {
		oldImport	= EMPTY_STRING;
		oldStart	= -1;
		oldEnd		= -1;
		newStart	= -1;
		newEnd		= -1;
		newImport	= null;
		isStatic	= false;
		isWildCard	= false;
		stay		= true;
		renamed		= false;
	}
	/** import, 完全修飾クラス名の変換
	 * {@link #newImport}は変換後に追記してもよいが、短くしてはならない */
	private Converter(final StringBuilder buf, final String oldImport, final Matcher matcher) {
		this.oldImport	= oldImport;
		newImport		= buf != null ? buf : new StringBuilder();
		isStatic		= matcher.start(2) >= 0;
		isWildCard		= matcher.start(11) >= 0;
		stay			= false;
		final boolean	withImport;
		final int		brandStart	= matcher.start(5);
		{	// import部
			final int	importEnd	= matcher.end(1);
			withImport	= importEnd >= 0;
			if (withImport) {
				newImport.append(oldImport, 0, importEnd);
			}
		}
		{
			final int	pkgStart	= matcher.start(3);
			// パッケージ部分の変換 (FQNまたはパッケージ単体の場合のみ処理)
			if (pkgStart >= 0) {
				newImport.append("org.apache.hop");

				final int	pkgEnd		= matcher.end(3);
				final var	pkgMatcher	= PACKAGE_PATTERN.matcher(oldImport).region(pkgStart, pkgEnd);
				int			lastEnd		= pkgStart;
				while (pkgMatcher.find()) {
					final int	partStart	= pkgMatcher.start();
					if (partStart > lastEnd) {
						newImport.append(oldImport, lastEnd, partStart);
					}
					final int	partEnd	= pkgMatcher.end()
							,	partLength	= partEnd - partStart;
					lastEnd = partEnd;
					if ("entries".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("actions");
					} else if ("entry".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("action");
					} else if ("job".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("workflow");
					} else if ("repository".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("metadata");
					} else if ("shared".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("variables");
					} else if ("step".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("transform");
					} else if ("trans".regionMatches(0, oldImport, partStart, partLength)) {
						newImport.append("pipeline");
					}
				}
				newImport.append(oldImport, lastEnd, pkgEnd);
				// パッケージの後ろに付いているセパレーターを引き継ぐ
				final int	separator	= matcher.start(4);
				if (separator >= 0) newImport.append(oldImport.charAt(separator));
			}
		}

		// Class部分の組み立て (クラス名ブロックが存在する場合のみ処理)
		// group(4) が有効であれば、そこから後ろにクラス名のコンポーネントが存在します
		RENAME: {
			if (brandStart >= 0) {
				final int	suffixStart	= matcher.start(9);
				if (suffixStart >= 0) { // Interface, Listener
					newImport.append('I');
				}

				// 内側のgroup(5)が有効＝"Kettle" が確定
				if (matcher.start(6) >= 0) {
					newImport.append("Hop");
				} else { // Kettle以外のプレフィックス(Base等)はそのまま流用
					newImport.append(oldImport, brandStart, matcher.end(5));
				}
				{
					// group(6): 旧ドメイン名 (Job / JobEntry / Step / Trans)
					final int	mainStart	= matcher.start(7);
					if (mainStart > 0) {
						switch (oldImport.charAt(mainStart)) {
							case 'J':
								// Job → Workflow, JobEntry → Action
								newImport.append(mainStart + 3 < matcher.end(7) ? "Action" : "Workflow");
								break;
							case 'S':
								// Step → Transform
								newImport.append("Transform");
								break;
							case 'T':
								// Trans → Pipeline
								newImport.append("Pipeline");
								break;
							case 'V':
								// VFS → Vfs
								newImport.append("Vfs");
								break;
							case 'X':
								// XML → Xml
								newImport.append("Xml");
								break;
						}
					}
				}
				{	// group(8): 付属品(Meta等)
					final int	optionalStart	= matcher.start(8);
					if (optionalStart >= 0) {
						newImport.append(oldImport, optionalStart, matcher.end(8));
					}
				}
				if (suffixStart >= 0) {
					// group(9): Listener / RowMeta
					switch (oldImport.charAt(suffixStart)) {
						case 'I':	break;
						case 'V':
							newImport.append("Variables");
							break;
						default:
							newImport.append(oldImport, suffixStart, matcher.end(9));
					}
				}
				{	// group(10): InnerClass / Constant
					final int	extraStart	= matcher.start(10)
							,	extraEnd	= matcher.end(10);
					if (extraStart < extraEnd) {
						newImport.append(oldImport, extraStart, extraEnd);
					}
				}
				if (!isWildCard) {
					// 最後のパートが変わったかどうか調べる
					oldStart	= oldImport.lastIndexOf('.') + 1;
					oldEnd		= matcher.end();
					newStart	= newImport.lastIndexOf(DOT) + 1;
					newEnd		= newImport.length();
					final int	partLen	= newImport.length() - newStart;
					renamed		= !((partLen == oldEnd - oldStart) &&
							oldImport.regionMatches(oldStart, getNewName(), 0, partLen));
					break RENAME;
				}
			}
			// クラス名がない、ワイルドカードのため最後のパーツがない
			oldStart	= -1;
			oldEnd		= -1;
			newStart	= -1;
			newEnd		= -1;
			renamed		= false;
		}
		if (withImport) {
			if (isWildCard) {
				newImport.append(".*");
			}
			newImport.append(';');
		}
	}
	/** 古い末端名
	 * {@link #oldEnd}が決まるまでは呼び出さない */
	@NotNull
	public String getOldName() {
		if (oldName == null) {
			oldName	= oldStart >= 0 ? oldImport.substring(oldStart, oldEnd) : EMPTY_STRING;
		}
		return oldName;
	}
	/** 新しい末端名
	 * {@link #newEnd}が決まるまでは呼び出さない */
	@NotNull
	public String getNewName() {
		if (newName == null) {
			newName	= newStart >= 0 ? newImport.substring(newStart, newEnd) : EMPTY_STRING;
		}
		return newName;
	}
	@Override
	public String toString() {
		return newImport != null ? newImport.toString() : EMPTY_STRING;
	}
}