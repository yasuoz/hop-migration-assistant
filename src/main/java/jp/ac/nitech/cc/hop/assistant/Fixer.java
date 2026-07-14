package jp.ac.nitech.cc.hop.assistant;

import static jp.ac.nitech.cc.hop.assistant.constructor.HopConstructorInspection.*;

/**
 * Action, Trans系のinterface, baseClassを管理する列挙型。
 * すべてのプロパティを不変（final）に指定し、高速かつ安全に管理します。
 */
public enum Fixer {
	ACTION_DIALOG(
			"org.apache.hop.ui.workflow.action.ActionDialog",
			"org.apache.hop.workflow.action.IActionDialog",
			DIALOG
	),
	TRANS(
			"org.apache.hop.pipeline.transform.BaseTransform",
			"org.apache.hop.pipeline.transform.ITransform",
			null
	),
	TRANS_DIALOG(
			"org.apache.hop.ui.pipeline.transform.BaseTransformDialog",
			"org.apache.hop.pipeline.transform.ITransformDialog",
			DIALOG
	),
	TRANS_DATA(
			"org.apache.hop.pipeline.transform.BaseTransformData",
			"org.apache.hop.pipeline.transform.ITransformData",
			DATA
	),
	TRANS_META(
			"org.apache.hop.pipeline.transform.BaseTransformMeta",
			"org.apache.hop.pipeline.transform.ITransformMeta",
			META
	);

	public final String	baseClass;
	public final String	interfaceClass;
	public final String	postfix;

	Fixer(final String baseClass, final String interfaceClass, final String postfix) {
		this.baseClass		= baseClass;
		this.interfaceClass	= interfaceClass;
		this.postfix		= postfix;
	}

	/**
	 * 接尾辞(Dialog等)を取り除いたベースの名称を返します。
	 * 引き算を排除したインデックス指定により高速に動作します。
	 */
	public String base(final String name) {
		if (name == null) return null;
		return (postfix == null) ? name : name.substring(0, name.length() - postfix.length());
	}

	/**
	 * ベースの名称に接尾辞を付与した完全な名称を返します。
	 */
	public void name(final StringBuilder buf, final String base) {
		if (base == null) return;
		buf.append(base);
		if (postfix != null) {
			buf.append(postfix);
		}
	}
}