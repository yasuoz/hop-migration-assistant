package jp.ac.nitech.cc.hop.assistant.constructor;

import com.intellij.psi.PsiElement;
import jp.ac.nitech.cc.hop.assistant.Fixer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;

public final class ConstructorTrans extends ConstructorBase {
	private final Expand	meta, data;
	ConstructorTrans(@NotNull final PsiElement element, final String base) {
		super(element);
		final var	buf	= new StringBuilder(base);
		final int	len	= base.length();
		meta	= new Expand(buf.append(META).toString(), Fixer.TRANS_META.interfaceClass);
		buf.setLength(len);
		data	= new Expand(buf.append(DATA).toString(), Fixer.TRANS_DATA.interfaceClass);
	}

	@Override
	protected String constructor(@NotNull String className) {
		return """
				public %s(
					final %s\ttransformMeta,
					final %s\tmeta,
					final %s\tdata,
					final int\tcopyNr,
					final %s\tpipelineMeta,
					final %s\tpipeline
				) {
					super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
					// TODO: Not yet implemented
				}
				""".formatted(className, TRANS_META, meta.fixed, data.fixed, PIPELINE_META, PIPELINE);
	}

	@Override
	protected List<Expand> expectedTypes() {
		return List.of(EX_TRANS_META, meta, data, EX_INT, EX_PIPELINE_META, EX_PIPELINE);
	}
}