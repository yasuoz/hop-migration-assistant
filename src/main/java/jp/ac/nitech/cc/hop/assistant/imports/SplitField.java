package jp.ac.nitech.cc.hop.assistant.imports;

import com.intellij.psi.*;
import jp.ac.nitech.cc.hop.assistant.edit.EditList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static jp.ac.nitech.cc.hop.assistant.imports.Converter.EMPTY_STRING;

/** クラスフィールドを分ける */
public class SplitField {
	private static final FieldPair[]	EMPTY_PAIR_LIST	= new FieldPair[0];
	public final Map<String,FieldPair>	fields;
	/** 改行を含むインデント文字 */
	public final String					indent;
	private final PsiClass				aClass;
	private final String				definition;
	private final FieldPair[]			pairList;
	public SplitField(final PsiField field) {
		aClass	= field.getContainingClass();
		final PsiField	firstField;
		SEARCH: {
			final boolean	isFirstChild	= isFirstField(field)
					,		isLastChild		= isLastField(field);
			if (isFirstChild && isLastChild) {
				final var	me	= new FieldPair(this, field, -2);
				fields		= Map.of(field.getName(), me);
				pairList	= EMPTY_PAIR_LIST;
				indent		= indent(field);
				break SEARCH;
			}
			final ArrayList<FieldPair>	fields	= new ArrayList<>();
			if (isFirstChild) {
				firstField	= field;
			} else {
				// 左側を探す
				for (PsiField left = field;;) {
					final PsiField	sibling	= getSiblingField(left, false);
					if (sibling == null) {	// フォールバック
						firstField	= fields.isEmpty() ? field : fields.getLast().field;
						break;
					} else {
						final FieldPair	current	= new FieldPair(this, sibling, 0);
						fields.add(current);
						if (isFirstField(sibling)) {
							firstField	= sibling;
							break;
						}
					}
					left	= sibling;
				}
				{
					final int	max	= fields.size();
					if (max > 1) {
						Collections.reverse(fields);
						for (int index = 1; index < max; index++) {
							fields.set(index, new FieldPair(fields.get(index), index));
						}
					}
				}
			}
			indent	= indent(firstField);
			fields.add(new FieldPair(this, field, fields.size()));
			if (!isLastChild) {
				// 右側を探す
				for (PsiField right = field;;) {
					final PsiField	sibling	= getSiblingField(right, true);
					if (sibling != null) {
						fields.add(new FieldPair(this, sibling, fields.size()));
						if (!isLastField(sibling)) {
							right	= sibling;
							continue;
						}
					}
					break;
				}
			}
			pairList	= fields.toArray(EMPTY_PAIR_LIST);
			{
				final LinkedHashMap<String,FieldPair>	map	= new LinkedHashMap<>(fields.size());
				for (final FieldPair pair : fields) {
					map.put(pair.name, pair);
				}
				this.fields	= Collections.unmodifiableMap(map);
			}
			if (fields.size() > 1) {
				final StringBuilder	buf	= new StringBuilder();
				{	// フィールド本体
					final String	fieldText	= firstField.getText();
					buf.append(fieldText, 0, firstField.getNameIdentifier().getStartOffsetInParent());
				}
				definition	= buf.toString();
				return;
			}
		}
		definition	= EMPTY_STRING;
	}
	public void fix(final EditList reserves) {
		for(final FieldPair pair : pairList) {
			// 処理済
			if (!pair.result.isEmpty()) continue;
			// 現状維持
			if (!(pair.startAffected || pair.endAffected)) continue;
			final var	field	= pair.getField();
			if (field == null) continue;
			final StringBuilder	buf	= pair.result;
			if (pair.startAffected) {
				buf.append(indent).append(definition);
			}
			pair.appendIdentifier(pair.endAffected);
			if (pair.endAffected) {
				buf.append(';').append(indent);
			}
			reserves.replace(pair.start, field.getTextRange().getEndOffset(), buf.toString());
		}
	}
	/** 隣り合った{@link PsiField}を取得 */
	private static PsiField getSiblingField(final PsiField field, final boolean forward) {
		final Function<PsiElement,PsiElement>	next	= forward ? PsiElement::getNextSibling : PsiElement::getPrevSibling;
		PsiElement	sibling	= field;
		for (;;) {
			final PsiElement	element = next.apply(sibling);
			if (element != null) {
				if (element instanceof final PsiField nextField) return nextField;
				if (element instanceof PsiWhiteSpace || element instanceof PsiJavaToken) {
					sibling	= element;
					continue;
				}
			}
			return null;	// JavaTokenやMethodが出たら境界を越えている
		}
	}
	/** {@param field}前にある空白を取得 */
	private static String indent(final PsiField field) {
		final var		prevSibling	= field.getPrevSibling();
		final String	prevSpace;
		final int		lastNewLine;
		if (prevSibling instanceof PsiWhiteSpace) {
			prevSpace	= prevSibling.getText();
			lastNewLine	= prevSpace.lastIndexOf('\n');
			return	lastNewLine == 0 ? prevSpace :
					lastNewLine > 0 ? prevSpace.substring(lastNewLine) :
							'\n' + prevSpace;
		}
		// フォールバック
		return "\n\t";
	}
	/** 冒頭の型定義がある事を確認 */
	private static boolean isFirstField(final PsiField field) {
		final PsiElement	firstChild	= field.getFirstChild();
		return !(firstChild instanceof PsiIdentifier) &&	// valueではない
				(firstChild instanceof PsiModifierList ||	// public, private, Annotation等
						firstChild instanceof PsiTypeElement);		// String, int等
	}
	/** セミコロンで終わっていることを確認 */
	private static boolean isLastField(final PsiField field) {
		return	(field.getLastChild() instanceof final PsiJavaToken javeToken
				&& javeToken.getTokenType() == JavaTokenType.SEMICOLON) ||
				(field.getNextSibling() instanceof final PsiJavaToken nextToken
				&& nextToken.getTokenType() == JavaTokenType.SEMICOLON);
	}
	@Override
	public String toString() {
		return definition;
	}
	public static class FieldPair {
		public final SplitField		parent;
		public final String			name;
		/** 中身が空っぽでない場合に、内容物を書き換える */
		public final StringBuilder	result	= new StringBuilder();
		/** {@link #pairList}での位置 */
		private final int			index;
		/** {@link PsiField}の冒頭部から、末尾コンマやコロン+whitespaceまでの間 */
		private final int			start, end;
		/** 分岐処理を経るとinvalidになるので注意 */
		private PsiField			field;
		/** 前が切られた */
		private boolean				startAffected	= false;
		/** 後ろが切られた */
		private boolean				endAffected		= false;
		private FieldPair(final SplitField parent, final PsiField field, final int index) {
			this.parent	= parent;
			this.field	= field;
			this.name	= field.getName();
			this.index	= index;
			this.start	= field.getTextRange().getStartOffset();
			PsiElement current	= field;
			for (;;) {
				final PsiElement	next	= current.getNextSibling();
				if (next instanceof PsiWhiteSpace) {
					current	= next;
				} else if(next instanceof final PsiJavaToken token) {
					final var	type	= token.getTokenType();
					if (type == JavaTokenType.COMMA) {
						current	= next;
					} else if (type == JavaTokenType.SEMICOLON) {
						current	= next;
						break;
					}
				} else break;
			}
			this.end	= current.getTextRange().getEndOffset();
		}

		private FieldPair(final FieldPair copy, final int index) {
			this.parent	= copy.parent;
			this.field	= copy.field;
			this.name	= copy.name;
			this.index	= index;
			this.start	= copy.start;
			this.end	= copy.end;
		}
		/** 定義構文を追記
		 * @param trim	末尾の「,;」を削り取る */
		private void appendIdentifier(final boolean trim) {
			final PsiField	field	= getField();
			final String	text	= field.getText();
			final int		start	= field.getNameIdentifier().getStartOffsetInParent();
			final int		max		= text.length();
			if (trim) {
				for (int i = max; i > start;) {
					final int	j	= i - 1;
					final char	c	= text.charAt(j);
					if (!Character.isWhitespace(c)) {
						switch (c) {
							case ',',';':
								break;
							default:
								result.append(text, start, i);
								return;
						}
					}
					i	= j;
				}
			}
			result.append(text, start, max);
		}
		/** 念のため、連続する最初のアクセスではこのメソッドを呼び出してください<br/>
		 * {@link #field}が上書きされます */
		public PsiField getField() {
			if (field == null || !field.isValid()) {
				field	= parent.aClass.findFieldByName(name, false);
			}
			return field;
		}
		@Nullable
		public FieldPair getPrevious() {
			return index > 0 ? parent.pairList[index - 1] : null;
		}
		@Nullable
		public FieldPair getNext() {
			if (index >= 0) {
				final int	next	= index + 1;
				return next < parent.pairList.length ? parent.pairList[next] : null;
			}
			return null;
		}
		public boolean isStay() {
			return result.isEmpty();
		}
		public void simplify(final EditList reserves) {
			if (parent.definition.isEmpty()) {
				reserves.insert(this.start, result.toString());
			} else {
				{
					final FieldPair	prev	= getPrevious();
					if (prev != null)	prev.endAffected	= true;
					final FieldPair	next	= getNext();
					if (next != null)	next.startAffected	= true;
				}
				result.append(parent.definition);
				appendIdentifier(true);
				result.append(';').append(parent.indent);
				reserves.replace(this.start, end, result.toString());
			}
		}
		public String toString() {
			return name;
		}
	}
}