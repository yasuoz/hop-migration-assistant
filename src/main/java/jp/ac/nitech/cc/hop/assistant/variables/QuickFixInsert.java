package jp.ac.nitech.cc.hop.assistant.variables;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

public class QuickFixInsert implements LocalQuickFix {
	private final PsiExpression	anchorElement;
	private final String		variableName;

	public QuickFixInsert(final PsiExpression anchorElement, final String variableName) {
		this.anchorElement	= anchorElement;
		this.variableName	= variableName;
	}

	@Override
	public @NotNull String getFamilyName() {
		return "Insert IVariables argument";
	}

	@Override
	public @NotNull String getName() {
		return I18n.message("quickfix.hop.database.second.var.insert", variableName);
	}

	@Override
	public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
		if (!anchorElement.isValid()) return;

		// 挿入する新しいPsiExpression(変数名)を作成
		final PsiExpression	varExpression	= JavaPsiFacade.getElementFactory(project)
				.createExpressionFromText(variableName, anchorElement);

		// DatabaseMeta(anchorElement)の親である引数リスト(PsiExpressionList)を取得
		final PsiElement	argumentList	= anchorElement.getParent();
		if (argumentList != null) {
			// DatabaseMetaの直前に新変数を追加(IntelliJが自動でカンマも補完します)
			argumentList.addBefore(varExpression, anchorElement);
		}
	}
}