package jp.ac.nitech.cc.hop.assistant.imports;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.openapi.util.text.StringUtilRt.isEmpty;

/**
 * ファイル内のすべてのPentahoクラスをHopに置き換える
 */
public class QuickFixImport extends LocalQuickFixAndIntentionActionOnPsiElement {
	private static final String	DUMMY_JAVA	= "Dummy.java";
	QuickFixImport(@Nullable PsiElement element) {
		super(element);
	}

	@NotNull
	@Override
	public String getText() {
		return I18n.message("quickfix.hop.import.migration.name");
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return "PentahoToHopImportMigration";
	}

	@Override
	public void invoke(
			final @NotNull Project		project,
			final @NotNull PsiFile		file,
			final @Nullable Editor		editor,
			final @NotNull PsiElement	startElement,
			final @NotNull PsiElement	endElement
	) {
		if (!(file instanceof final PsiJavaFile original)) return;
		final PsiJavaFile	javaFile	= (PsiJavaFile) PsiFileFactory.getInstance(project)
				.createFileFromText(
						original.getName(),
						JavaLanguage.INSTANCE,
						original.getText(),
						false, // イベントを発生させず完全に隔離する
						false
				);
		final var	importList	= javaFile.getImportList();
		final var	fileFactory	= PsiFileFactory.getInstance(project);
		if (importList == null) return;

		final PsiImportStatementBase[]					allImports	= importList.getAllImportStatements();
		final ArrayList<PsiJavaCodeReferenceElement>	references;
		final ReplaceAll								replacer	= new ReplaceAll(project, original, javaFile);
		{
			// ファイル内のすべてのコード参照(型宣言、new、キャスト等)を全取得
			final var	javaList	= PsiTreeUtil.findChildrenOfType(javaFile, PsiJavaCodeReferenceElement.class);
			references	= new ArrayList<>(javaList.size());
			for (final PsiJavaCodeReferenceElement ref : javaList) {
				if (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null) continue;
				if (ref.getQualifier() != null) {
					if (ref.getParent() instanceof PsiJavaCodeReferenceElement ||
							!ref.getText().startsWith("org.pentaho")) continue;
					// -----------------------------------------------------------------
					// ここに到達したrefは、メソッドチェーンも含めた「一番右端」
					// 例: "org.pentaho.di...RowMetaInterface.static_method().getParentStepMeta"
					// -----------------------------------------------------------------
					// 右から左(子・クオリファイア方向)へ芋づる式に遡る
					//LOGGER.info("==右端: " + ref.getText() + "	- " + ref.getClass().getCanonicalName());
					for (PsiJavaCodeReferenceElement current = ref;;) {
						final String	fqn	= current.getText();
						if (fqn.indexOf('(') < 0 && isUpper(current.getReferenceName())) {
							final var convert = Converter.from(fqn);
							if (!convert.stay) {
								final PsiFile	dummyFile = fileFactory
										.createFileFromText(DUMMY_JAVA, JavaLanguage.INSTANCE, convert.toString(), false, false);
								replacer.replace(current, PsiTreeUtil.findChildOfType(dummyFile, PsiJavaCodeReferenceElement.class));
							}
							break;
						}
						// さらに左(子)へ移動
						if (current.getQualifier() instanceof PsiJavaCodeReferenceElement childRef) {
							current	= childRef;
						} else {
							break;
						}
					}
					continue;
				}
				// 最左端: (自分の左側にパッケージなどのクオリファイアが一切ない)要素
				// クラスが解決するものを除外
				if (ref.resolve() != null) continue;
				references.add(ref);
			}
			if (allImports.length == 0 && references.isEmpty()) return;
		}

		final var	nameFactory	= JavaPsiFacade.getInstance(project).getElementFactory();
		// PSI要素から「生テキスト」を引っこ抜き、Converterに丸投げして自律判定させる
		for (final PsiImportStatementBase imp : allImports) {
			// エディタ上に実際に書かれている「import static ... ;」の生テキストを最速取得
			final String	rawImport	= imp.getText();
			final var		converter	= Converter.from(rawImport);
			// 変換対象外
			if (converter.stay) continue;
			final String	newFqn		= converter.toString();
			if (converter.renamed) {
				// 古いShortNameと新しいShortNameを特定する
				final String	oldShortName	= converter.getOldName();
				final String	newShortName	= converter.getNewName();
				// 本文中のShortNameをセットで置き換え
				for (final PsiJavaCodeReferenceElement ref : references) {
					if (!oldShortName.equals(ref.getText())) continue;
					replacer.replace(ref, nameFactory.createReferenceFromText(newShortName, null));
				}
			}
			{	// クラスが実在していなくても、import構文チェックだけで置き換えるための方法
				final var	dummyFile	= (PsiJavaFile) fileFactory.createFileFromText(DUMMY_JAVA, JavaLanguage.INSTANCE, newFqn);
				final var	imports		= dummyFile.getImportList();
				if (imports != null) {
					replacer.replace(imp, imports.getAllImportStatements()[0]);
				}
			}
		}
		// 蓄積した変更を一挙に実行
		replacer.commit();
	}
	private static boolean isUpper(final String str) {
		return !isEmpty(str) && Character.isUpperCase(str.charAt(0));
	}

	private static class ReplaceAll implements ThrowableRunnable<IncorrectOperationException> {
		private final @NotNull Project		project;
		private final Document				document;
		private final PsiJavaFile			javaFile;
		private boolean						stay	= true;
		private ReplaceAll(final @NotNull Project project, final PsiJavaFile original, final PsiJavaFile javaFile) {
			this.project	= project;
			this.javaFile	= javaFile;
			document		= PsiDocumentManager.getInstance(project).getDocument(original);
		}

		private void commit() throws IncorrectOperationException {
			if (document == null || stay) return;
			WriteCommandAction.writeCommandAction(project, javaFile)
					.withName("Replace Multiple Imports").run(this);
		}
		private void replace(final PsiElement from, final PsiElement to) {
			if (to != null) {
				from.replace(to);
				stay	= false;
			}
		}
		@Override
		public void run() throws IncorrectOperationException {
			document.setText(javaFile.getText());
		}
	}
}