package jp.ac.nitech.cc.hop.assistant.imports;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import jp.ac.nitech.cc.hop.assistant.I18n;
import jp.ac.nitech.cc.hop.assistant.service.HopProjectSettingsService;
import org.jetbrains.annotations.NotNull;

public class HopImportInspection extends AbstractBaseJavaLocalInspectionTool {
	private static final boolean	IS_DEV_MODE	= "true".equals(System.getProperty("plugin.dev.mode"));
	@NotNull
	@Override
	public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
		if (emptyHopLibrary(holder)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}
		return new Migrator(holder);
	}
	public static boolean emptyHopLibrary(final @NotNull ProblemsHolder holder) {
		if (IS_DEV_MODE) return false;
		final var	settings	= holder.getProject().getService(HopProjectSettingsService.class);
		return settings == null || !settings.getHasHopLibrary();
	}
	private static class Migrator extends JavaElementVisitor {
		private final @NotNull ProblemsHolder	holder;
		private Migrator(@NotNull final ProblemsHolder holder) {
			this.holder	= holder;
		}
		@Override
		public void visitImportStatement(@NotNull final PsiImportStatement statement) {
			super.visitImportStatement(statement);
			handleImportCheck(statement, statement.getQualifiedName());
		}
		@Override
		public void visitImportStaticStatement(@NotNull final PsiImportStaticStatement statement) {
			super.visitImportStaticStatement(statement);
			final var	ref	= statement.getImportReference();
			if (ref != null) {
				handleImportCheck(statement, ref.getQualifiedName());
			}
		}
		private void handleImportCheck(@NotNull final PsiImportStatementBase statement, final String qName) {
			if (qName != null && qName.startsWith("org.pentaho.di")) {
				final var	importRef		= statement.getImportReference();

				// 万が一、構文が壊れていて参照が取れなかった場合のみ、安全のために文全体(statement)を対象にする
				final var	targetElement	= (importRef != null) ? importRef : statement;
				// 画面のインポート文の場所に警告(波線)を引き、QuickFixを登録する
				holder.registerProblem(
						targetElement,
						I18n.message("quickfix.hop.import.migration.name"),
						ProblemHighlightType.GENERIC_ERROR,
						new QuickFixImport(statement)
				);
			}
		}
	}
}