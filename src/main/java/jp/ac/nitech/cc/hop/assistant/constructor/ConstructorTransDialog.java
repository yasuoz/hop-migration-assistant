package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import jp.ac.nitech.cc.hop.assistant.Fixer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;

public final class ConstructorTransDialog extends ConstructorBase {
	private static final String	FIELD_NAME	= "meta";
	private final Expand		expected;
	ConstructorTransDialog(@NotNull final PsiElement element, final String expected) {
		super(element);
		this.expected	= new Expand(expected, Fixer.TRANS.interfaceClass);
	}

	/**
	 * Transform用の構造生成 (Shell, IVariables, 具象Meta, PipelineMeta)
	 */
	@Override
	protected String constructor(@NotNull final String className) {
		return """
				public %s(
					final %s\t\tparent,
					final %s\tvariables, 
					final %s\ttransformMeta, 
					final %s\t\tpipelineMeta
				) {
					super(parent, variables, transformMeta, pipelineMeta);
					this.%s\t= transformMeta;
					// TODO: Not yet implemented
				}
				""".formatted(className, SHELL, I_VARIABLES, this.expected.fixed, PIPELINE_META, FIELD_NAME);
	}
	@Override
	protected void createFields(final PsiClass psiClass) {
		createField(psiClass, expected, FIELD_NAME);
	}

	@Override
	protected List<Expand> expectedTypes() {
		return List.of(EX_SHELL, EX_VARIABLES, expected, EX_PIPELINE_META);
	}
}