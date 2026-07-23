package jp.ac.nitech.cc.hop.assistant.variables;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

public class QuickFixReplace implements LocalQuickFix {
	private final String	variableName;

	public QuickFixReplace(final String variableName) {
		this.variableName	= variableName;
	}

	@Override
	public @NotNull String getFamilyName() {
		return "Replace IVariables argument";
	}

	@Override
	public @NotNull String getName() {
		return I18n.message("quickfix.hop.database.second.var.replace", variableName);
	}

	@Override
	public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
		final PsiElement	element	= descriptor.getStartElement();
		if (element instanceof final PsiExpression oldExpression) {
			final PsiExpression	varExpression	= JavaPsiFacade.getElementFactory(project)
					.createExpressionFromText(variableName, element);
			oldExpression.replace(varExpression);
		}
	}
}