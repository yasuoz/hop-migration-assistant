package jp.ac.nitech.cc.hop.assistant.svg;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiFile;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.BridgeException;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.w3c.dom.Element;

import static jp.ac.nitech.cc.hop.assistant.svg.HopSvgFormatInspection.markElementError;

/**
 * GVTBuilder の要素処理メソッドを直接ハックし、
 * 各要素のエラーを個別に刈り取ってスキャンを強行するカスタムビルダー
 */
public class HopInspectionGVTBuilder extends GVTBuilder {
	private final PsiFile					psiFile;
	private final ProblemsHolder			holder;

	HopInspectionGVTBuilder(final PsiFile psiFile, final ProblemsHolder holder) {
		this.psiFile	= psiFile;
		this.holder		= holder;
	}

	/**
	 * 💡 核心：1つのXML要素をビルドする処理全体を完全にオーバーライドして包み込む
	 */
	@Override
	protected void buildGraphicsNode(final BridgeContext ctx, final Element e, final CompositeGraphicsNode parentNode) {
		try {
			// 元のGVTBuilderの堅牢なビルド処理（提示いただいた switch文や try-catch などのライフサイクルすべて）をそのまま実行
			super.buildGraphicsNode(ctx, e, parentNode);
		} catch (final BridgeException ex) {
			// 💡 ここでキャッチ！GVTBuilder自身の「throw var8;」も含めて、外側への脱出を完全にブロック
			markElementError(psiFile, ex, holder);

			// 💡 核心：例外を外に投げずに「正常終了（return）」させる！
			// これにより、呼び出し元のループは「この要素の処理は終わった」と判断し、
			// 次のXMLタグのbuildGraphicsNode の処理へと平然と進みます。
		} catch (final Exception ex) {
			// BridgeException以外の想定外のランタイム例外もここで防壁となり、スキャンを殺さない
			markElementError(psiFile, e, ex.getMessage(), holder);
		}
	}
}