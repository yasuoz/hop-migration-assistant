package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import jp.ac.nitech.cc.hop.assistant.Fixer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;

public final class ConstructorActionDialog extends ConstructorBase {
	private static final String	FIELD_NAME	= "action";
	private final Expand		expected;
	ConstructorActionDialog(@NotNull final PsiElement element, final String expected) {
		super(element);
		this.expected	= new Expand(expected, Fixer.ACTION.interfaceClass);
	}

	/**
	 * Action用の構造生成 (Shell, 具象Action, WorkflowMeta, IVariables)
	 */
	@Override
	protected String constructor(@NotNull final String className) {
		return """
				public %s(
					final %s\t\tparent,
					final %s\taction, 
					final %s\t\tworkflowMeta, 
					final %s\tvariables
				) {
					super(parent, workflowMeta, variables);
					this.%s\t= action;
					// TODO: Not yet implemented
				}
				""".formatted(className, SHELL, this.expected.fixed, WORKFLOW_META, I_VARIABLES, FIELD_NAME);
	}
	@Override
	protected void createFields(final PsiClass psiClass) {
		createField(psiClass, expected, FIELD_NAME);
	}

	@Override
	protected List<Expand> expectedTypes() {
		return List.of(EX_SHELL, expected, EX_WORKFLOW, EX_VARIABLES);
	}
}