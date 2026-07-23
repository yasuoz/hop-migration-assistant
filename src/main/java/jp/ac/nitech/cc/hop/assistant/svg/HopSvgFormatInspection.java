package jp.ac.nitech.cc.hop.assistant.svg;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.bridge.*;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.XMLResourceDescriptor;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.svg.SVGDocument;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jp.ac.nitech.cc.hop.assistant.imports.HopImportInspection.emptyHopLibrary;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class HopSvgFormatInspection extends LocalInspectionTool {
	private static final Pattern	PATTERN_EX	= Pattern.compile("(?!\\s)[^,\\]]+(?=\\s*]$)");
	private static final String		ID			= "id";

	@NotNull
	@Override
	public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
		if (emptyHopLibrary(holder)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}
		return new PsiElementVisitor() {
			@Override
			public void visitFile(@NotNull PsiFile file) {
				super.visitFile(file);

				final VirtualFile	virtualFile	= file.getVirtualFile();
				if (virtualFile == null) return;

				final String		name		= virtualFile.getName().toLowerCase(Locale.ROOT);
				if (!name.endsWith(".svg")) return;

				final String		path		= virtualFile.getPath();
				if (!path.contains("/resources/")) return;

				validateAllSvgErrors(file, virtualFile, holder);
			}
		};
	}

	private void validateAllSvgErrors(@NotNull final PsiFile psiFile, @NotNull final VirtualFile virtualFile, @NotNull final ProblemsHolder holder) {
		try {
			final var	parser	= XMLResourceDescriptor.getXMLParserClassName();
			final var	factory	= new SAXSVGDocumentFactory(parser);

			try (final var	inputStream	= virtualFile.getInputStream()) {
				final var	doc			= factory.createSVGDocument(virtualFile.getUrl(), inputStream);

				final BridgeContext	ctx;
				{
					// UserAgentAdapterをインラインでオーバーライドし、エラー通知を横取りする
					final UserAgentAdapter	userAgent	= new BridgeInteceptor(psiFile, holder);
					final DocumentLoader	loader		= new DocumentLoader(userAgent) {
						@Override
						public SVGDocument loadDocument(String uri) throws IOException {
							// Batik標準のDOM実装を取得
							final DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();

							// 最低限の <svg> タグだけを持った空の「ダミーSVGドキュメント」を作成して返す
							return (SVGDocument) impl.createDocument(
									SVGConstants.SVG_NAMESPACE_URI,
									SVGConstants.SVG_SVG_TAG,
									null
							);
						}
					};
					ctx	= new BridgeContext(userAgent, loader);
					ctx.setDynamic(false); // STATICモードと同等
				}
				final var	builder	= new HopInspectionGVTBuilder(psiFile, holder);
				try {
					// ここで内部的に複数のビルドエラーが起きた場合も、
					// userAgent.displayError(Exception) が何度も呼び出されます
					builder.build(ctx, doc);
				} catch (final BridgeException e) {
					// ビルダー自体が完全に停止してスローした最後の1件も漏らさず処理
					markElementError(psiFile, e.getElement(), e.getMessage(), holder);
				}
			}
		} catch (final DOMException e) {
			final var		message	= e.getMessage();
			final var		matcher	= PATTERN_EX.matcher(message);
			final int		index	= message.indexOf(' ');
			final String	message2;
			MESSAGE: {
				if (matcher.find()) {
					final String	tagName		= matcher.group();
					final XmlTag	targetTag	= findMatchingPsiTag(psiFile, tagName, null);
					if (targetTag != null) {
						holder.registerProblem(targetTag, I18n.message("inspection.hop.error.svg.standard.at", index > 0 ? message.substring(0, index) : tagName));
						return;
					}
					if (index > 0) {
						message2	= new StringBuilder().append(message, 0, index).append(":	").append(tagName).toString();
						break MESSAGE;
					}
				}
				message2	= message;
			}
			holder.registerProblem(psiFile, I18n.message("inspection.hop.error.svg.structure", message2));
		} catch (final Throwable e) {
			// XML構造自体の破損（閉じタグ忘れ等）をエディタに通知
			holder.registerProblem(psiFile, I18n.message("inspection.hop.error.svg.structure", e.getMessage()));
		}
	}

	/**
	 * 検出されたDOM要素をPsiTreeから見つけ出し、アラートを登録する補助メソッド
	 */
	static void markElementError(final PsiFile psiFile, final Element errorElement, final String message, final ProblemsHolder holder) {
		if (errorElement != null) {
			final String	tagName		= errorElement.getLocalName();
			final String	elementId	= errorElement.getAttribute(ID);

			final XmlTag	targetTag	= findMatchingPsiTag(psiFile, tagName, elementId);
			if (targetTag != null) {
				holder.registerProblem(targetTag, I18n.message("inspection.hop.error.svg.standard.at",  message));
				return;
			}
		}
		// 要素が特定できない汎用的な違反の場合
		holder.registerProblem(psiFile, I18n.message("inspection.hop.error.svg.standard",  message));
	}

	static void markElementError(final PsiFile psiFile, final BridgeException ex, final ProblemsHolder holder) {
		final Element	element	= ex.getElement();
		final String	code	= ex.getCode()
				,		message;
		{
			final String	reason	= ex.getMessage();
			final Matcher	matcher	= PATTERN_EX.matcher(reason);
			if (matcher.find()) {
				message	= new StringBuilder(code).append(":	").append(reason, matcher.start(), matcher.end()).toString();
			} else {
				message	= code;
			}
		}
		markElementError(psiFile, element, message, holder);
	}

	private static XmlTag findMatchingPsiTag(final PsiFile psiFile, final String tagName, @Nullable final String id) {
		if (tagName == null) return null;
		return findTagRecursive(psiFile, tagName, id);
	}

	private static XmlTag findTagRecursive(final PsiElement element, final String tagName, @Nullable final String id) {
		if (element instanceof final XmlTag tag) {
			if (tagName.equals(tag.getLocalName())) {
				if (isEmpty(id) || id.equals(tag.getAttributeValue(ID))) {
					return tag;
				}
			}
		}
		for (final PsiElement child : element.getChildren()) {
			final XmlTag result = findTagRecursive(child, tagName, id);
			if (result != null) return result;
		}
		return null;
	}

	private static class BridgeInteceptor extends UserAgentAdapter {
		@NotNull
		final PsiFile			psiFile;
		@NotNull
		final ProblemsHolder	holder;
		private BridgeInteceptor(final @NotNull PsiFile psiFile, final @NotNull ProblemsHolder holder) {
			this.psiFile	= psiFile;
			this.holder		= holder;
		}
		@Override
		public void displayError(final String message) {
			// ログ出力用、またはコンテキスト要素がない汎用エラーのハンドリング
		}

		@Override
		public void displayError(final Exception e) {
			// 例外が発生したとき、それがBridgeExceptionなら要素を特定してアラート登録
			if (e instanceof final BridgeException be) {
				markElementError(psiFile, be, holder);
			}
		}
	}
}