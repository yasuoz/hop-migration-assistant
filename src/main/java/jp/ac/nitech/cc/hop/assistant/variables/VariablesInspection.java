package jp.ac.nitech.cc.hop.assistant.variables;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import jp.ac.nitech.cc.hop.assistant.I18n;
import org.jetbrains.annotations.NotNull;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.I_VARIABLES;
import static jp.ac.nitech.cc.hop.assistant.imports.HopImportInspection.emptyHopLibrary;

public class VariablesInspection extends AbstractBaseJavaLocalInspectionTool {
	private static final String	DATABASE_META_FQCN	= "org.apache.hop.core.database.DatabaseMeta";

	@Override
	public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
		if (emptyHopLibrary(holder)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}
		return new JavaElementVisitor() {
			@Override
			public void visitNewExpression(final @NotNull PsiNewExpression expression) {
				super.visitNewExpression(expression);
				final PsiJavaCodeReferenceElement	classReference	= expression.getClassReference();
				if (classReference == null || !"Database".equals(classReference.getReferenceName())) {
					return;
				}

				final PsiExpressionList	argumentList	= expression.getArgumentList();
				if (argumentList == null || argumentList.getExpressionCount() < 2) {
					return;
				}
				final PsiExpression[]	arguments = argumentList.getExpressions();

				final PsiExpression	secondArg		= arguments[1];
				final PsiType		secondArgType	= secondArg.getType();

				// すでに第2引数がIVariables型なら問題ないのでスルー
				if (TypeUtils.typeEquals(I_VARIABLES, secondArgType)) {
					return;
				}
				// 第2引数がIVariablesではない場合、現在のクラスの階層からIVariables型のフィールドを探索
				final PsiClass		containingClass	= PsiTreeUtil.getParentOfType(expression, PsiClass.class);
				final PsiVariable	variablesField	= findVariablesInHierarchy(containingClass);
				if (variablesField == null) return;
				final String		variableName	= variablesField.getName();
				if (arguments.length == 2) {
					// 2項目目がDatabaseMetaかどうか判定
					if (TypeUtils.typeEquals(DATABASE_META_FQCN, secondArgType)) {
						holder.registerProblem(argumentList,
								I18n.message("quickfix.hop.database.second.var.insert", variableName),
								ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
								new QuickFixInsert(secondArg, variableName)
						);
					}
				} else {
					holder.registerProblem(secondArg,
							I18n.message("quickfix.hop.database.second.var.replace", variableName),
							ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
							new QuickFixReplace(variableName)
					);
				}
			}
		};
	}

	/**
	 * クラスのフィールドを遡り、型がIVariablesに完全一致するものを探す
	 */
	private PsiVariable findVariablesInHierarchy(final PsiClass psiClass) {
		if (psiClass == null) return null;

		for (final PsiField field : psiClass.getFields()) {
			// フル修飾名が完全に一致するかだけをシンプルにチェック
			if (TypeUtils.typeEquals(I_VARIABLES, field.getType())) {
				return field;
			}
		}
		// 親クラスに向かって階層を遡る
		return findVariablesInHierarchy(psiClass.getSuperClass());
	}
}