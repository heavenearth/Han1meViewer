package com.yenaly.han1meviewer.ui.fragment.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.itxca.spannablex.spannable
import com.yenaly.han1meviewer.HA1_GITHUB_FORUM_URL
import com.yenaly.han1meviewer.HA1_GITHUB_ISSUE_URL
import com.yenaly.han1meviewer.HA1_GITHUB_RELEASES_URL
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.activity.AboutActivity
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.view.MaterialDialogPreference
import com.yenaly.han1meviewer.ui.viewmodel.SettingsViewModel
import com.yenaly.han1meviewer.util.checkNeedUpdate
import com.yenaly.han1meviewer.util.hanimeVideoLocalFolder
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.ActivitiesManager
import com.yenaly.yenaly_libs.base.preference.LongClickablePreference
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.appLocalVersionName
import com.yenaly.yenaly_libs.utils.browse
import com.yenaly.yenaly_libs.utils.copyToClipboard
import com.yenaly.yenaly_libs.utils.folderSize
import com.yenaly.yenaly_libs.utils.formatFileSize
import com.yenaly.yenaly_libs.utils.showShortToast
import com.yenaly.yenaly_libs.utils.startActivity
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/01 001 14:25
 */
class HomeSettingsFragment : YenalySettingsFragment(R.xml.settings_home),
    IToolbarFragment<SettingsActivity> {

    private val viewModel by activityViewModels<SettingsViewModel>()

    companion object {
        const val VIDEO_LANGUAGE = "video_language"
        const val PLAYER_SETTINGS = "player_settings"
        const val H_KEYFRAME_SETTINGS = "h_keyframe_settings"
        const val UPDATE = "update"
        const val ABOUT = "about"
        const val DOWNLOAD_PATH = "download_path"
        const val CLEAR_CACHE = "clear_cache"
        const val SUBMIT_BUG = "submit_bug"
        const val FORUM = "forum"
        const val NETWORK_SETTINGS = "network_settings"
    }

    private val videoLanguage
            by safePreference<MaterialDialogPreference>(VIDEO_LANGUAGE)
    private val playerSettings
            by safePreference<Preference>(PLAYER_SETTINGS)
    private val hKeyframeSettings
            by safePreference<Preference>(H_KEYFRAME_SETTINGS)
    private val update
            by safePreference<Preference>(UPDATE)
    private val about
            by safePreference<Preference>(ABOUT)
    private val downloadPath
            by safePreference<LongClickablePreference>(DOWNLOAD_PATH)
    private val clearCache
            by safePreference<Preference>(CLEAR_CACHE)
    private val submitBug
            by safePreference<Preference>(SUBMIT_BUG)
    private val forum
            by safePreference<Preference>(FORUM)
    private val networkSettings
            by safePreference<Preference>(NETWORK_SETTINGS)

    private var checkUpdateTimes = 0

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        videoLanguage.apply {

            // 從 xml 轉移至此
            entries = arrayOf("繁體中文", "簡體中文")
            entryValues = arrayOf("zh-CHT", "zh-CHS")
            // 不能直接用 defaultValue 设置，没效果
            if (value == null) setValueIndex(0)

            setOnPreferenceChangeListener { _, newValue ->
                if (newValue != Preferences.videoLanguage) {
                    requireContext().showAlertDialog {
                        setCancelable(false)
                        setTitle("注意！")
                        setMessage("修改影片語言需要重啟程式，否則不起作用！")
                        setPositiveButton(R.string.confirm) { _, _ ->
                            ActivitiesManager.restart(killProcess = true)
                        }
                        setNegativeButton(R.string.cancel, null)
                    }
                }
                return@setOnPreferenceChangeListener true
            }
        }
        playerSettings.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_homeSettingsFragment_to_playerSettingsFragment)
            return@setOnPreferenceClickListener true
        }
        hKeyframeSettings.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_homeSettingsFragment_to_hKeyframeSettingsFragment)
            return@setOnPreferenceClickListener true
        }
        about.apply {
            title = buildString {
                append(getString(R.string.about))
                append(" ")
                append(getString(R.string.hanime_app_name))
            }
            summary = getString(R.string.current_version, "v${appLocalVersionName}")
            setOnPreferenceClickListener {
                startActivity<AboutActivity>()
                return@setOnPreferenceClickListener true
            }
        }
        downloadPath.apply {
            val path = hanimeVideoLocalFolder?.path
            summary = path
            setOnPreferenceClickListener {
                requireContext().showAlertDialog {
                    setTitle("不允許更改")
                    setMessage(
                        "詳細位置：${path}\n" + "長按選項可以複製！"
                    )
                    setPositiveButton("OK", null)
                }
                return@setOnPreferenceClickListener true
            }
            setOnPreferenceLongClickListener {
                path.copyToClipboard()
                showShortToast(R.string.copy_to_clipboard)
                return@setOnPreferenceLongClickListener true
            }
        }
        clearCache.apply {
            val cacheDir = context.cacheDir
            var folderSize = cacheDir?.folderSize ?: 0L
            summary = generateClearCacheSummary(folderSize)
            // todo: strings.xml
            setOnPreferenceClickListener {
                if (folderSize != 0L) {
                    context.showAlertDialog {
                        setTitle("請再次確認一遍")
                        setMessage("確定要清除快取嗎？")
                        setPositiveButton(R.string.confirm) { _, _ ->
                            thread {
                                if (cacheDir?.deleteRecursively() == true) {
                                    folderSize = cacheDir.folderSize
                                    activity?.runOnUiThread {
                                        showShortToast("清除成功")
                                        summary = generateClearCacheSummary(folderSize)
                                    }
                                } else {
                                    folderSize = cacheDir.folderSize
                                    activity?.runOnUiThread {
                                        showShortToast("清除發生意外")
                                        summary = generateClearCacheSummary(folderSize)
                                    }
                                }
                            }
                        }
                        setNegativeButton(R.string.cancel, null)
                    }
                } else showShortToast("當前快取為空，無需清理哦")
                return@setOnPreferenceClickListener true
            }
        }
        submitBug.apply {
            setOnPreferenceClickListener {
                browse(HA1_GITHUB_ISSUE_URL)
                return@setOnPreferenceClickListener true
            }
        }
        forum.apply {
            setOnPreferenceClickListener {
                browse(HA1_GITHUB_FORUM_URL)
                return@setOnPreferenceClickListener true
            }
        }
        networkSettings.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_homeSettingsFragment_to_networkSettingsFragment)
                return@setOnPreferenceClickListener true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initFlow()
    }

    private fun initFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            whenStarted {
                viewModel.versionFlow.collect { state ->
                    when (state) {
                        is WebsiteState.Error -> {
                            checkUpdateTimes++
                            update.setSummary(R.string.check_update_failed)
                            update.setOnPreferenceClickListener {
                                if (checkUpdateTimes > 2) {
                                    showUpdateFailedDialog()
                                } else viewModel.getLatestVersion()
                                return@setOnPreferenceClickListener true
                            }
                        }

                        is WebsiteState.Loading -> {
                            update.setSummary(R.string.checking_update)
                            update.onPreferenceClickListener = null
                        }

                        is WebsiteState.Success -> {
                            if (checkNeedUpdate(state.info.tagName)) {
                                update.summary =
                                    getString(R.string.check_update_success, state.info.tagName)
                                update.setOnPreferenceClickListener {
                                    browse(state.info.assets.first().browserDownloadURL)
                                    return@setOnPreferenceClickListener true
                                }
                            } else {
                                update.setSummary(R.string.already_latest_update)
                                update.onPreferenceClickListener = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showUpdateFailedDialog() {
        requireContext().showAlertDialog {
            setTitle("別檢查了！別檢查了！")
            setMessage(
                """
                更新接口走的是 Github，所以每天有下載限制，如果你發現軟體有重大問題但是提示更新失敗，請直接去 Github Releases 介面查看是否有最新版下載。
                
                還有我竟然發現有人花錢買這 APP，真沒必要哈！
            """.trimIndent()
            )
            setPositiveButton("帶我去下載") { _, _ ->
                browse(HA1_GITHUB_RELEASES_URL)
            }
            setNegativeButton(R.string.cancel, null)
        }
    }

    private fun generateClearCacheSummary(size: Long): CharSequence {
        return spannable {
            size.formatFileSize().span {
                style(Typeface.BOLD)
            }
            " ".text()
            getString(R.string.cache_occupy).text()
        }
    }

    override fun SettingsActivity.setupToolbar() {
        supportActionBar!!.setTitle(R.string.settings)
    }
}