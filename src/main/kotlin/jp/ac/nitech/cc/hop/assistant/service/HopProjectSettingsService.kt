package jp.ac.nitech.cc.hop.assistant.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** Hopを使うプロジェクトと使わないプロジェクトで分離 */
@Service(Service.Level.PROJECT)
class HopProjectSettingsService(val project: Project) {
	/** Hopライブラリがクラスパスに存在する */
	@Volatile
	var hasHopLibrary: Boolean = false
}