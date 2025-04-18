/*
 * Copyright 2016-2020 Open Food Facts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package openfoodfacts.github.scrachx.openfood.repositories

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.squareup.picasso.Picasso
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.Credentials
import openfoodfacts.github.scrachx.openfood.models.*
import openfoodfacts.github.scrachx.openfood.models.entities.additive.*
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.*
import openfoodfacts.github.scrachx.openfood.models.entities.analysistag.AnalysisTag
import openfoodfacts.github.scrachx.openfood.models.entities.analysistag.AnalysisTagNameDao
import openfoodfacts.github.scrachx.openfood.models.entities.analysistagconfig.AnalysisTagConfig
import openfoodfacts.github.scrachx.openfood.models.entities.analysistagconfig.AnalysisTagConfigDao
import openfoodfacts.github.scrachx.openfood.models.entities.brand.Brand
import openfoodfacts.github.scrachx.openfood.models.entities.category.*
import openfoodfacts.github.scrachx.openfood.models.entities.country.Country
import openfoodfacts.github.scrachx.openfood.models.entities.ingredient.*
import openfoodfacts.github.scrachx.openfood.models.entities.label.*
import openfoodfacts.github.scrachx.openfood.models.entities.states.States
import openfoodfacts.github.scrachx.openfood.models.entities.states.StatesName
import openfoodfacts.github.scrachx.openfood.models.entities.states.StatesNameDao
import openfoodfacts.github.scrachx.openfood.models.entities.store.Store
import openfoodfacts.github.scrachx.openfood.models.entities.tag.Tag
import openfoodfacts.github.scrachx.openfood.network.ApiFields
import openfoodfacts.github.scrachx.openfood.network.services.AnalysisDataAPI
import openfoodfacts.github.scrachx.openfood.network.services.RobotoffAPI
import openfoodfacts.github.scrachx.openfood.utils.getLoginPreferences
import org.greenrobot.greendao.query.WhereCondition.StringCondition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This is a repository class which implements repository interface.
 *
 * @author Lobster
 * @since 03.03.18
 */
@Singleton
class ProductRepository @Inject constructor(
        @ApplicationContext private val context: Context,
        private val daoSession: DaoSession,
        private val analysisDataApi: AnalysisDataAPI,
        private val robotoffApi: RobotoffAPI,
        private val taxonomiesManager: TaxonomiesManager
) {

    /**
     * Load labels from the server or local database
     *
     * @return The list of Labels.
     */
    fun reloadLabelsFromServer() =
            taxonomiesManager.getTaxonomyData(Taxonomy.LABEL, true, daoSession.labelDao, this)

    fun loadLabels(lastModifiedDate: Long) = analysisDataApi.getLabels()
            .map { it.map() }
            .doOnSuccess { labels ->
                saveLabels(labels)
                updateLastDownloadDateInSettings(Taxonomy.LABEL, lastModifiedDate)
            }

    /**
     * Load tags from the server or local database
     *
     * @return The list of Tags.
     */
    fun reloadTagsFromServer() = taxonomiesManager.getTaxonomyData(
            Taxonomy.TAGS,
            true,
            daoSession.tagDao,
            this
    )

    fun loadTags(lastModifiedDate: Long) = analysisDataApi.getTags()
            .map { it.tags }
            .doOnSuccess {
                saveTags(it)
                updateLastDownloadDateInSettings(Taxonomy.TAGS, lastModifiedDate)
            }

    fun reloadInvalidBarcodesFromServer() = taxonomiesManager.getTaxonomyData(
            Taxonomy.INVALID_BARCODES,
            true,
            daoSession.invalidBarcodeDao,
            this
    )

    fun loadInvalidBarcodes(lastModifiedDate: Long) = analysisDataApi.getInvalidBarcodes()
            .map { strings -> strings.map { InvalidBarcode(it) } }
            .doOnSuccess {
                saveInvalidBarcodes(it)
                updateLastDownloadDateInSettings(Taxonomy.INVALID_BARCODES, lastModifiedDate)
            }

    /**
     * Load allergens from the server or local database
     *
     * @return The allergens in the product.
     */
    fun reloadAllergensFromServer(): Single<List<Allergen>> =
            // FIXME: this returns 404
            taxonomiesManager.getTaxonomyData(Taxonomy.ALLERGEN, true, daoSession.allergenDao, this)

    fun getAllergens(): Single<List<Allergen>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.ALLERGEN, false, daoSession.allergenDao, this)

    fun loadAllergens(lastModifiedDate: Long): Single<List<Allergen>> = analysisDataApi.getAllergens()
            .map { it.map() }
            .doOnSuccess {
                saveAllergens(it)
                updateLastDownloadDateInSettings(Taxonomy.ALLERGEN, lastModifiedDate)
            }

    /**
     * Load countries from the server or local database
     *
     * @return The list of countries.
     */
    fun reloadCountriesFromServer(): Single<List<Country>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.COUNTRY, true, daoSession.countryDao, this)

    fun loadCountries(lastModifiedDate: Long): Single<List<Country>> = analysisDataApi.getCountries()
            .map { it.map() }
            .doOnSuccess {
                saveCountries(it)
                updateLastDownloadDateInSettings(Taxonomy.COUNTRY, lastModifiedDate)
            }

    /**
     * Load categories from the server or local database
     *
     * @return The list of categories.
     */
    fun reloadCategoriesFromServer() =
            taxonomiesManager.getTaxonomyData(Taxonomy.CATEGORY, true, daoSession.categoryDao, this)

    fun getCategories() = taxonomiesManager.getTaxonomyData(Taxonomy.CATEGORY, false, daoSession.categoryDao, this)

    fun loadCategories(lastModifiedDate: Long) = analysisDataApi.getCategories()
            .map { it.map() }
            .doOnSuccess {
                saveCategories(it)
                updateLastDownloadDateInSettings(Taxonomy.CATEGORY, lastModifiedDate)
            }

    /**
     * Load allergens which user selected earlier (i.e user's allergens)
     *
     * @return The list of allergens.
     */
    fun getEnabledAllergens(): List<Allergen> =
            daoSession.allergenDao.queryBuilder()
                    .where(AllergenDao.Properties.Enabled.eq("true"))
                    .list()

    /**
     * Load additives from the server or local database
     *
     * @return The list of additives.
     */
    fun reloadAdditivesFromServer() =
            taxonomiesManager.getTaxonomyData(Taxonomy.ADDITIVE, true, daoSession.additiveDao, this)

    fun loadAdditives(lastModifiedDate: Long) = analysisDataApi.getAdditives()
            .map { it.map() }
            .doOnSuccess {
                saveAdditives(it)
                updateLastDownloadDateInSettings(Taxonomy.ADDITIVE, lastModifiedDate)
            }

    /**
     * TODO to be improved by loading only in the user language ?
     * Load ingredients from (the server or) local database
     * If SharedPreferences lastDownloadIngredients is set try this :
     * if file from the server is newer than last download delete database, load the file and fill database,
     * else if database is empty, download the file and fill database,
     * else return the content from the local database.
     *
     * @return The ingredients in the product.
     */
    fun reloadIngredientsFromServer(): Single<List<Ingredient>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.INGREDIENT, true, daoSession.ingredientDao, this)

    fun loadIngredients(lastModifiedDate: Long): Single<List<Ingredient>> = analysisDataApi.getIngredients()
            .map { it.map() }
            .doOnSuccess {
                saveIngredients(it)
                updateLastDownloadDateInSettings(Taxonomy.INGREDIENT, lastModifiedDate)
            }

    /**
     * Load states from the server or local database
     *
     * @return The list of states.
     */
    fun reloadStatesFromServer(): Single<List<States>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.STATES, true, daoSession.statesDao, this)

    fun loadStates(lastModifiedDate: Long): Single<List<States>> = analysisDataApi.getStates()
            .map { it.map() }
            .doOnSuccess {
                saveState(it)
                updateLastDownloadDateInSettings(Taxonomy.STATES, lastModifiedDate)
            }

    /**
     * Load stores from the server or local database
     *
     * @return The list of stores.
     */
    fun reloadStoresFromServer(): Single<List<Store>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.STORES, true, daoSession.storeDao, this)

    fun loadStores(lastModifiedDate: Long): Single<List<Store>> = analysisDataApi.getStores()
            .map { it.map() }
            .doOnSuccess {
                saveStores(it)
                updateLastDownloadDateInSettings(Taxonomy.STORES, lastModifiedDate)
            }

    fun reloadBrandsFromServer(): Single<List<Brand>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.BRANDS, true, daoSession.brandDao, this)

    fun loadBrands(lastModifiedDate: Long): Single<List<Brand>> = analysisDataApi.getBrands()
            .map { it.map() }
            .doOnSuccess {
                saveBrands(it)
                updateLastDownloadDateInSettings(Taxonomy.BRANDS, lastModifiedDate)
            }

    /**
     * This function set lastDownloadtaxonomy setting
     *
     * @param taxonomy Name of the taxonomy (allergens, additives, categories, countries, ingredients, labels, tags)
     * @param lastDownload Date of last update on Long format
     */
    private fun updateLastDownloadDateInSettings(taxonomy: Taxonomy, lastDownload: Long) {
        context.getSharedPreferences("prefs", 0)
                .edit { putLong(taxonomy.lastDownloadTimeStampPreferenceId, lastDownload) }
        Log.i(LOG_TAG, "Set lastDownload of $taxonomy to $lastDownload")
    }

    /**
     * Labels saving to local database
     *
     * @param labels The list of labels to be saved.
     *
     *
     * Label and LabelName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveLabels(labels: List<Label>) {
        daoSession.database.beginTransaction()
        try {
            labels.forEach { label ->
                daoSession.labelDao.insertOrReplace(label)
                label.names.forEach { daoSession.labelNameDao.insertOrReplace(it) }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveLabels", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Tags saving to local database
     *
     * @param tags The list of tags to be saved.
     */
    private fun saveTags(tags: List<Tag>) = daoSession.tagDao.insertOrReplaceInTx(tags)

    /**
     * Invalid Barcodess saving to local database. Will clear all previous invalid barcodes stored before.
     *
     * @param invalidBarcodes The list of invalidBarcodes to be saved.
     */
    private fun saveInvalidBarcodes(invalidBarcodes: List<InvalidBarcode>) {
        daoSession.invalidBarcodeDao.deleteAll()
        daoSession.invalidBarcodeDao.insertInTx(invalidBarcodes)
    }

    /**
     * Allergens saving to local database
     *
     * @param allergens The list of allergens to be saved.
     *
     *
     * Allergen and AllergenName has One-To-Many relationship, therefore we need to save them separately.
     */
    fun saveAllergens(allergens: List<Allergen>) {
        daoSession.database.beginTransaction()
        try {
            allergens.forEach { allergen ->
                daoSession.allergenDao.insertOrReplace(allergen)
                allergen.names.forEach { daoSession.allergenNameDao.insertOrReplace(it) }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveAllergens", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Additives saving to local database
     *
     * @param additives The list of additives to be saved.
     *
     *
     * Additive and AdditiveName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveAdditives(additives: List<Additive>) {
        daoSession.database.beginTransaction()
        try {
            additives.forEach { additive ->
                daoSession.additiveDao.insertOrReplace(additive)
                additive.names.forEach { daoSession.additiveNameDao.insertOrReplace(it) }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveAdditives", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Countries saving to local database
     *
     * @param countries The list of countries to be saved.
     *
     *
     * Country and CountryName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveCountries(countries: List<Country>) {
        daoSession.database.beginTransaction()
        try {
            countries.forEach { country ->
                daoSession.countryDao.insertOrReplace(country)
                country.names.forEach { daoSession.countryNameDao.insertOrReplace(it) }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveCountries", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Categories saving to local database
     *
     * @param categories The list of categories to be saved.
     *
     *
     * Category and CategoryName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveCategories(categories: List<Category>) {
        daoSession.database.beginTransaction()
        try {
            categories.forEach { category ->
                daoSession.categoryDao.insertOrReplace(category)
                category.names.forEach { name ->
                    daoSession.categoryNameDao.insertOrReplace(name)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveCategories", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Delete rows from Ingredient, IngredientName and IngredientsRelation
     * set the autoincrement to 0
     */
    fun deleteIngredientCascade() {
        daoSession.ingredientDao.deleteAll()
        daoSession.ingredientNameDao.deleteAll()
        daoSession.ingredientsRelationDao.deleteAll()
        val daoSession = daoSession
        daoSession.database.execSQL("""update sqlite_sequence set seq=0 where name in 
            |('${daoSession.ingredientDao.tablename}', 
            |'${daoSession.ingredientNameDao.tablename}', 
            |'${daoSession.ingredientsRelationDao.tablename}')""".trimMargin())
    }

    /**
     * TODO to be improved by loading only if required and only in the user language
     * Ingredients saving to local database
     *
     * @param ingredients The list of ingredients to be saved.
     *
     *
     * Ingredient and IngredientName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveIngredients(ingredients: List<Ingredient>) {
        daoSession.database.beginTransaction()
        try {
            ingredients.forEach { ingredient ->
                daoSession.ingredientDao.insertOrReplace(ingredient)
                ingredient.names.forEach {
                    daoSession.ingredientNameDao.insertOrReplace(it)
                }
                ingredient.parents.forEach {
                    daoSession.ingredientsRelationDao.insertOrReplace(it)
                }
                ingredient.children.forEach {
                    daoSession.ingredientsRelationDao.insertOrReplace(it)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveIngredients", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * States saving to local database
     *
     * @param states The list of states to be saved.
     *
     *
     * states and statesName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveState(states: List<States>) {
        daoSession.database.beginTransaction()
        try {
            states.forEach { state ->
                daoSession.statesDao.insertOrReplace(state)
                state.names.forEach {
                    daoSession.statesNameDao.insertOrReplace(it)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveStates", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Stores saving to local database
     *
     * @param stores The list of stores to be saved.
     *
     *
     * store and storeName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveStores(stores: List<Store>) {
        daoSession.database.beginTransaction()
        try {
            stores.forEach { store ->
                daoSession.storeDao.insertOrReplace(store)
                store.names.forEach {
                    daoSession.storeNameDao.insertOrReplace(it)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveStores", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Save Brands to local Database
     *
     * @param brands The list of brands to be stored
     *
     */
    private fun saveBrands(brands: List<Brand>) {
        daoSession.database.beginTransaction()
        try {
            brands.forEach { brand ->
                daoSession.brandDao.insertOrReplace(brand)
                brand.names.forEach {
                    daoSession.brandNameDao.insertOrReplace(it)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveBrands", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    /**
     * Changes enabled field of allergen and updates it.
     *
     * @param isEnabled depends on whether user selected or unselected the allergen
     * @param allergenTag is unique Id of allergen
     */
    fun setAllergenEnabled(allergenTag: String, isEnabled: Boolean) = Completable.fromCallable {
        daoSession.allergenDao.queryBuilder().where(
                AllergenDao.Properties.Tag.eq(allergenTag)
        ).unique()?.let {
            it.enabled = isEnabled
            daoSession.allergenDao.update(it)
        }
    }

    /**
     * Loads translated label from the local database by unique tag of label and language code
     *
     * @param labelTag is a unique Id of label
     * @param languageCode is a 2-digit language code
     * @return The translated label
     */
    fun getLabelByTagAndLanguageCode(labelTag: String?, languageCode: String?) = Single.fromCallable {
        daoSession.labelNameDao.queryBuilder().where(
                LabelNameDao.Properties.LabelTag.eq(labelTag),
                LabelNameDao.Properties.LanguageCode.eq(languageCode)
        ).unique() ?: LabelName()
    }

    /**
     * Loads translated label from the local database by unique tag of label and default language code
     *
     * @param labelTag is a unique Id of label
     * @return The translated label
     */
    fun getLabelByTagAndDefaultLanguageCode(labelTag: String?) =
            getLabelByTagAndLanguageCode(labelTag, ApiFields.Defaults.DEFAULT_LANGUAGE)

    /**
     * Loads translated additive from the local database by unique tag of additive and language code
     *
     * @param additiveTag is a unique Id of additive
     * @param languageCode is a 2-digit language code
     * @return The translated additive name
     */
    fun getAdditiveByTagAndLanguageCode(additiveTag: String?, languageCode: String?) = Single.fromCallable {
        daoSession.additiveNameDao.queryBuilder().where(
                AdditiveNameDao.Properties.AdditiveTag.eq(additiveTag),
                AdditiveNameDao.Properties.LanguageCode.eq(languageCode)
        ).unique() ?: AdditiveName()
    }

    /**
     * Loads translated additive from the local database by unique tag of additive and default language code
     *
     * @param additiveTag is a unique Id of additive
     * @return The translated additive tag
     */
    fun getAdditiveByTagAndDefaultLanguageCode(additiveTag: String?) =
            getAdditiveByTagAndLanguageCode(additiveTag, ApiFields.Defaults.DEFAULT_LANGUAGE)

    fun getCountries() = taxonomiesManager.getTaxonomyData(
            Taxonomy.COUNTRY,
            false,
            daoSession.countryDao,
            this
    )

    fun getCountryByCC2OrWorld(cc2: String?) = getCountries().flatMapMaybe { countries ->
        countries.asSequence()
                .filter { it.cc2.equals(cc2, ignoreCase = true) }
                .firstOrNull()
                .let { if (it == null) Maybe.empty() else Maybe.just(it) }
    }

    /**
     * Loads translated category from the local database by unique tag of category and language code
     *
     * @param categoryTag is a unique Id of category
     * @param languageCode is a 2-digit language code
     * @return The translated category name
     */
    fun getCategoryByTagAndLanguageCode(
            categoryTag: String,
            languageCode: String = ApiFields.Defaults.DEFAULT_LANGUAGE
    ) = Single.fromCallable {
        daoSession.categoryNameDao.queryBuilder().where(
                CategoryNameDao.Properties.CategoryTag.eq(categoryTag),
                CategoryNameDao.Properties.LanguageCode.eq(languageCode)
        ).unique() ?: CategoryName().apply {
            this.name = categoryTag
            this.categoryTag = categoryTag
            this.isWikiDataIdPresent = false
        }
    }

    /**
     * Loads list of translated category names from the local database by language code
     *
     * @param languageCode is a 2-digit language code
     * @return The translated list of category name
     */
    fun getAllCategoriesByLanguageCode(languageCode: String?) = Single.fromCallable {
        daoSession.categoryNameDao.queryBuilder()
                .where(CategoryNameDao.Properties.LanguageCode.eq(languageCode))
                .orderAsc(CategoryNameDao.Properties.Name)
                .list()
    }

    /**
     * Loads list of category names from the local database by default language code
     *
     * @return The list of category name
     */
    fun getAllCategoriesByDefaultLanguageCode() = getAllCategoriesByLanguageCode(ApiFields.Defaults.DEFAULT_LANGUAGE)

    /**
     * Loads translated and selected/unselected allergens.
     *
     * @param isEnabled depends on whether allergen was selected or unselected by user
     * @param languageCode is a 2-digit language code
     * @return The list of allergen names
     */
    fun getAllergensByEnabledAndLanguageCode(isEnabled: Boolean?, languageCode: String?) = Single.fromCallable {
        val allergens = daoSession.allergenDao.queryBuilder().where(AllergenDao.Properties.Enabled.eq(isEnabled)).list()
                ?: return@fromCallable emptyList()
        allergens.mapNotNull {
            daoSession.allergenNameDao.queryBuilder().where(
                    AllergenNameDao.Properties.AllergenTag.eq(it.tag),
                    AllergenNameDao.Properties.LanguageCode.eq(languageCode)
            ).unique()
        }
    }.subscribeOn(Schedulers.io())

    /**
     * Loads all translated allergens.
     *
     * @param languageCode is a 2-digit language code
     * @return The list of translated allergen names
     */
    fun getAllergensByLanguageCode(languageCode: String?) = Single.fromCallable {
        daoSession.allergenNameDao.queryBuilder()
                .where(AllergenNameDao.Properties.LanguageCode.eq(languageCode))
                .list()
    }

    /**
     * Loads translated allergen from the local database by unique tag of allergen and language code
     *
     * @param allergenTag is a unique Id of allergen
     * @param languageCode is a 2-digit language code
     * @return The translated allergen name
     */
    fun getAllergenByTagAndLanguageCode(allergenTag: String?, languageCode: String?) = Single.fromCallable {
        daoSession.allergenNameDao.queryBuilder()
                .where(AllergenNameDao.Properties.AllergenTag.eq(allergenTag),
                        AllergenNameDao.Properties.LanguageCode.eq(languageCode))
                .unique()
                ?: AllergenName().apply {
                    this.name = allergenTag
                    this.allergenTag = allergenTag
                    this.isWikiDataIdPresent = false
                }
    }.subscribeOn(Schedulers.io())

    /**
     * Loads translated allergen from the local database by unique tag of allergen and default language code
     *
     * @param allergenTag is a unique Id of allergen
     * @return The translated allergen name
     */
    fun getAllergenByTagAndDefaultLanguageCode(allergenTag: String?) =
            getAllergenByTagAndLanguageCode(allergenTag, ApiFields.Defaults.DEFAULT_LANGUAGE)

    /**
     * Loads translated states from the local database by unique tag of states and language code
     *
     * @param statesTag is a unique Id of states
     * @param languageCode is a 2-digit language code
     * @return The translated states name
     */
    fun getStatesByTagAndLanguageCode(statesTag: String, languageCode: String?) = Single.fromCallable {
        daoSession.statesNameDao.queryBuilder()
                .where(StatesNameDao.Properties.StatesTag.eq(statesTag),
                        StatesNameDao.Properties.LanguageCode.eq(languageCode))
                .unique()
                ?: StatesName(statesTag, statesTag.split(":").component1(), statesTag.split(":").component2())
    }.subscribeOn(Schedulers.io())


    /**
     * Loads Robotoff question from the local database by code and lang of question.
     *
     * @param code for the question
     * @param lang is language of the question
     * @return The single question
     */
    fun getProductQuestion(code: String, lang: String) = robotoffApi.getProductQuestions(code, lang, 1)
            .map { it.questions }
            .flatMapMaybe { if (it.isEmpty()) Maybe.empty() else Maybe.just(it[0]) }

    /**
     * Annotate the Robotoff insight response using insight id and annotation
     *
     * @param insightId is the unique id for the insight
     * @param annotation is the annotation to be used
     * @return The annotated insight response
     */
    fun annotateInsight(insightId: String, annotation: AnnotationAnswer): Single<AnnotationResponse> {
        // if the user is logged in, send the auth, otherwise make it anonymous
        val user = context.getLoginPreferences().getString("user", "")?.trim { it <= ' ' } ?: ""
        val pass = context.getLoginPreferences().getString("pass", "")?.trim { it <= ' ' } ?: ""

        return if (user.isBlank() || pass.isBlank()) {
            robotoffApi.annotateInsight(insightId, annotation.result)
        } else {
            robotoffApi.annotateInsight(insightId, annotation.result, Credentials.basic(user, pass, Charsets.UTF_8))
        }
    }

    /**
     * Load analysis tags from the server or local database
     *
     * @return The analysis tags in the product.
     */
    fun reloadAnalysisTagsFromServer() =
            taxonomiesManager.getTaxonomyData(Taxonomy.ANALYSIS_TAGS, true, daoSession.analysisTagDao, this)

    fun loadAnalysisTags(lastModifiedDate: Long) = analysisDataApi.getAnalysisTags()
            .map { it.map() }
            .doOnSuccess {
                saveAnalysisTags(it)
                updateLastDownloadDateInSettings(Taxonomy.ANALYSIS_TAGS, lastModifiedDate)
            }

    /**
     * AnalysisTags saving to local database
     *
     * @param tags The list of analysis tags to be saved.
     *
     *
     * AnalysisTag and AnalysisTagName has One-To-Many relationship, therefore we need to save them separately.
     */
    private fun saveAnalysisTags(tags: List<AnalysisTag>) {
        daoSession.database.beginTransaction()
        try {
            tags.forEach { tag ->
                daoSession.analysisTagDao.insertOrReplace(tag)
                tag.names.forEach {
                    daoSession.analysisTagNameDao.insertOrReplace(it)
                }
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveAnalysisTags", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    fun reloadAnalysisTagConfigsFromServer(): Single<List<AnalysisTagConfig>> =
            taxonomiesManager.getTaxonomyData(Taxonomy.ANALYSIS_TAG_CONFIG, true, daoSession.analysisTagConfigDao, this)

    fun loadAnalysisTagConfigs(lastModifiedDate: Long): Single<List<AnalysisTagConfig>> = analysisDataApi.getAnalysisTagConfigs()
            .map { it.map() }
            .doOnSuccess {
                saveAnalysisTagConfigs(it)
                updateLastDownloadDateInSettings(Taxonomy.ANALYSIS_TAG_CONFIG, lastModifiedDate)
            }

    private fun saveAnalysisTagConfigs(analysisTagConfigs: List<AnalysisTagConfig>) {
        daoSession.database.beginTransaction()
        try {
            analysisTagConfigs.forEach {
                Picasso.get().load(it.iconUrl).fetch()
                daoSession.analysisTagConfigDao.insertOrReplace(it)
            }
            daoSession.database.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "saveAnalysisTagConfigs", e)
        } finally {
            daoSession.database.endTransaction()
        }
    }

    private fun updateAnalysisTagConfig(analysisTagConfig: AnalysisTagConfig?, languageCode: String) {
        if (analysisTagConfig != null) {
            var analysisTagName = daoSession.analysisTagNameDao.queryBuilder().where(
                    AnalysisTagNameDao.Properties.AnalysisTag.eq(analysisTagConfig.analysisTag),
                    AnalysisTagNameDao.Properties.LanguageCode.eq(languageCode)
            ).unique()
            if (analysisTagName == null) {
                analysisTagName = daoSession.analysisTagNameDao.queryBuilder().where(
                        AnalysisTagNameDao.Properties.AnalysisTag.eq(analysisTagConfig.analysisTag),
                        AnalysisTagNameDao.Properties.LanguageCode.eq(ApiFields.Defaults.DEFAULT_LANGUAGE)
                ).unique()
            }
            analysisTagConfig.name = analysisTagName
            val type = "en:${analysisTagConfig.type}"
            var analysisTagTypeName = daoSession.analysisTagNameDao.queryBuilder()
                    .where(AnalysisTagNameDao.Properties.AnalysisTag.eq(type),
                            AnalysisTagNameDao.Properties.LanguageCode.eq(languageCode))
                    .unique()
            if (analysisTagTypeName == null) {
                analysisTagTypeName = daoSession.analysisTagNameDao.queryBuilder()
                        .where(AnalysisTagNameDao.Properties.AnalysisTag.eq(type),
                                AnalysisTagNameDao.Properties.LanguageCode.eq(ApiFields.Defaults.DEFAULT_LANGUAGE))
                        .unique()
            }
            analysisTagConfig.typeName = if (analysisTagTypeName != null) analysisTagTypeName.name else analysisTagConfig.type
        }
    }

    /**
     * @param analysisTag
     * @param languageCode
     * @return [Maybe.empty] if no analysis tag found
     */
    fun getAnalysisTagConfigByTagAndLanguageCode(analysisTag: String?, languageCode: String) = Maybe.fromCallable {
        daoSession.analysisTagConfigDao.queryBuilder()
                .where(AnalysisTagConfigDao.Properties.AnalysisTag.eq(analysisTag))
                .unique().also { updateAnalysisTagConfig(it, languageCode) }
    }.subscribeOn(Schedulers.io())

    fun getUnknownAnalysisTagConfigsByLanguageCode(languageCode: String) = Single.fromCallable {
        daoSession.analysisTagConfigDao.queryBuilder()
                .where(StringCondition("""${AnalysisTagConfigDao.Properties.AnalysisTag.columnName} LIKE "%unknown%""""))
                .list().onEach { updateAnalysisTagConfig(it, languageCode) }
    }.subscribeOn(Schedulers.io())


    companion object {
        private val LOG_TAG = ProductRepository::class.simpleName
    }
}
