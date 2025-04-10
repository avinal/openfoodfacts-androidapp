package openfoodfacts.github.scrachx.openfood.features.changelog

import android.content.SharedPreferences
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.analytics.SentryAnalytics
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabsHelper
import openfoodfacts.github.scrachx.openfood.customtabs.WebViewFallback
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ChangelogDialog : DialogFragment(R.layout.fragment_changelog) {

    companion object {
        private const val TAG = "changelog_dialog"
        private const val FORCE_SHOW_KEY = "force_show"
        private const val LAST_VERSION_CODE = "last_version_code"
        private const val URL_CROWDIN = "https://crowdin.com/project/openfoodfacts"

        fun newInstance(forceShow: Boolean): ChangelogDialog {
            val args = Bundle().apply {
                putBoolean(FORCE_SHOW_KEY, forceShow)
            }
            return ChangelogDialog().apply {
                arguments = args
            }
        }
    }

    @Inject
    lateinit var sentryAnalytics: SentryAnalytics

    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var translationHelpLabel: TextView
    private lateinit var recyclerView: RecyclerView
    private val compositeDisposable = CompositeDisposable()

    override fun getTheme(): Int = R.style.OFFTheme_NoActionBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        translationHelpLabel = view.findViewById(R.id.changelog_label_help)
        recyclerView = view.findViewById(R.id.changelog_recycler)
        view.findViewById<View>(R.id.changelog_button_close).setOnClickListener { dismiss() }

        applyWindowTweaks()
        setupTranslationHelpLabel()
        setupRecyclerView()
    }

    override fun onDestroyView() {
        compositeDisposable.clear()
        super.onDestroyView()
    }

    @Suppress("DEPRECATION")
    fun presentAutomatically(activity: AppCompatActivity) {
        arguments?.let {
            if (it.getBoolean(FORCE_SHOW_KEY, false)) {
                show(activity.supportFragmentManager, TAG)
            } else {
                try {
                    val lastVersionCode = getVersion()
                    val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                    if (currentVersionCode >= 0 && currentVersionCode > lastVersionCode) {
                        show(activity.supportFragmentManager, TAG)
                        saveVersionCode(currentVersionCode)
                    }
                } catch (ex: NameNotFoundException) {
                    sentryAnalytics.record(ex)
                }
            }
        }
    }

    private fun setupTranslationHelpLabel() {
        val locale = localeManager.getLocale()
        if (locale.language.startsWith(Locale.ENGLISH.language)) {
            translationHelpLabel.isVisible = false
        } else {
            translationHelpLabel.text = getString(R.string.changelog_translation_help, locale.displayLanguage)
            translationHelpLabel.isVisible = true
            translationHelpLabel.setOnClickListener { openDailyFoodFacts() }
        }
    }

    private fun applyWindowTweaks() {
        dialog?.window?.run {
            decorView.setPadding(0, 0, 0, 0)
            attributes.gravity = Gravity.BOTTOM
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT
            attributes.height = WindowManager.LayoutParams.MATCH_PARENT
            setWindowAnimations(R.style.ChangelogDialogAnimation)
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        val changelogService = ChangelogService(requireContext(), localeManager)
        compositeDisposable.add(
                changelogService
                        .observeChangelog()
                        .map { changelog ->
                            val itemList = mutableListOf<ChangelogListItem>()
                            changelog.versions.forEach { version ->
                                itemList.add(ChangelogListItem.Header(version.name, version.date))
                                version.items.forEach { item ->
                                    itemList.add(ChangelogListItem.Item("- $item"))
                                }
                            }
                            itemList
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { items -> recyclerView.adapter = ChangelogAdapter(items) },
                                { throwable -> sentryAnalytics.record(throwable) }
                        )
        )
    }

    private fun openDailyFoodFacts() {
        val dailyFoodFactUri = Uri.parse(URL_CROWDIN)
        val customTabActivityHelper = CustomTabActivityHelper().apply {
            mayLaunchUrl(dailyFoodFactUri, null, null)
        }
        val customTabsIntent = CustomTabsHelper.getCustomTabsIntent(
                requireActivity(),
                customTabActivityHelper.session,
        )
        CustomTabActivityHelper.openCustomTab(
                requireActivity(),
                customTabsIntent,
                dailyFoodFactUri,
                WebViewFallback()
        )
    }

    private fun saveVersionCode(versionCode: Long) {
        sharedPreferences
                .edit()
                .putLong(LAST_VERSION_CODE, versionCode)
                .apply()
    }

    private fun getVersion(): Long = sharedPreferences.getLong(LAST_VERSION_CODE, 0)
}
