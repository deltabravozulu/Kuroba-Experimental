package com.github.adamantcheese.chan.core.repository;

import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.common.SuspendableInitializer;
import com.github.adamantcheese.json.JsonSettings;
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import javax.inject.Inject;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import static com.github.adamantcheese.chan.core.site.SiteRegistry.SITE_CLASSES;

public class SiteRepository {
    private static final String TAG = "SiteRepository";

    private DatabaseManager databaseManager;
    private Sites sitesObservable = new Sites();
    private SuspendableInitializer<Unit> suspendableInitializer = new SuspendableInitializer<Unit>("SiteRepository");

    public Site forId(int id) {
        return sitesObservable.forId(id);
    }

    @Inject
    public SiteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Sites all() {
        return sitesObservable;
    }

    public SiteModel byId(int id) {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().byId(id));
    }

    @Nullable
    public Site bySiteDescriptor(SiteDescriptor siteDescriptor) {
        for (Site site : all().sites) {
            if (site.siteDescriptor().equals(siteDescriptor)) {
                return site;
            }
        }

        return null;
    }

    public boolean containsSite(SiteDescriptor siteDescriptor) {
        return bySiteDescriptor(siteDescriptor) != null;
    }

    public void setId(SiteModel siteModel, int id) {
        databaseManager.runTask(databaseManager.getDatabaseSiteManager().updateId(siteModel, id));
    }

    public void updateSiteUserSettingsAsync(SiteModel siteModel, JsonSettings jsonSettings) {
        siteModel.storeUserSettings(jsonSettings);
        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager().update(siteModel));
    }

    public Map<Integer, Integer> getOrdering() {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().getOrdering());
    }

    public void updateSiteOrderingAsync(List<Site> sites) {
        List<Integer> ids = new ArrayList<>(sites.size());
        for (Site site : sites) {
            ids.add(site.id());
        }

        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager().updateOrdering(ids), r -> {
            sitesObservable.wasReordered();
            sitesObservable.notifyObservers();
        });
    }

    public boolean isReady() {
        return suspendableInitializer.isInitialized();
    }

    public void invokeAfterInitialized(Function1<Throwable, Unit> func) {
        suspendableInitializer.invokeAfterInitialized(func);
    }

    public void initialize() {
//        try {
//            List<Site> sites = new ArrayList<>();
//
//            List<SiteModel> models = databaseManager.runTask(
//                    databaseManager.getDatabaseSiteManager().getAll()
//            );
//
//            for (SiteModel siteModel : models) {
//                SiteConfigSettingsHolder holder;
//                try {
//                    holder = instantiateSiteFromModel(siteModel);
//                } catch (IllegalArgumentException e) {
//                    Logger.e(TAG, "instantiateSiteFromModel", e);
//                    break;
//                }
//
//                Site site = holder.site;
//                SiteConfig config = holder.config;
//                JsonSettings settings = holder.settings;
//                site.initialize(siteModel.id, settings);
//
//                sites.add(site);
//            }
//
//            sitesObservable.addAll(sites);
//
//            for (Site site : sites) {
//                site.postInitialize();
//            }
//
//            sitesObservable.notifyObservers();
//            suspendableInitializer.initWithValue(Unit.INSTANCE);
//        } catch (Throwable error) {
//            Logger.e(TAG, "Error while initializing SiteRepository", error);
//            suspendableInitializer.initWithError(error);
//        }
    }

    public Site createFromClass(Class<? extends Site> siteClass) {
        Site site = instantiateSiteClass(siteClass);

//        SiteConfig config = new SiteConfig();
//        JsonSettings settings = new JsonSettings();
//
//        //the index doesn't necessarily match the key value to get the class ID anymore since sites were removed
//        config.classId = SITE_CLASSES.keyAt(SITE_CLASSES.indexOfValue(site.getClass()));
//        config.external = false;
//
//        SiteModel model = createFromClass(config, settings);
//        site.initialize(model.id, settings);
//        sitesObservable.add(site);
//
//        site.postInitialize();
//        sitesObservable.notifyObservers();

        return site;
    }

    private SiteModel createFromClass(SiteConfig config, JsonSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        siteModel.storeConfig(config);
        siteModel.storeUserSettings(userSettings);
        databaseManager.runTask(databaseManager.getDatabaseSiteManager().add(siteModel));

        return siteModel;
    }

    private SiteConfigSettingsHolder instantiateSiteFromModel(SiteModel siteModel) {
        Pair<SiteConfig, JsonSettings> configFields = siteModel.loadConfigFields();
        SiteConfig config = configFields.first;
        JsonSettings settings = configFields.second;

        return new SiteConfigSettingsHolder(instantiateSiteClass(config.classId), config, settings);
    }

    private Site instantiateSiteClass(int classId) {
        Class<? extends Site> clazz = SITE_CLASSES.get(classId);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown class id: " + classId);
        }
        return instantiateSiteClass(clazz);
    }

    private Site instantiateSiteClass(Class<? extends Site> clazz) {
        Site site;
        try {
            site = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException();
        }
        return site;
    }

    public void removeSite(Site site) {
        databaseManager.runTask(() -> {
            removeFilters(site);
            databaseManager.getDatabaseBoardManager().deleteBoards(site).call();
            databaseManager.getDatabaseSavedReplyManager().deleteSavedReplies(site).call();
            databaseManager.getDatabaseHideManager().deleteThreadHides(site).call();
            databaseManager.getDatabaseSiteManager().deleteSite(site).call();
            return null;
        });
    }

    private void removeFilters(Site site)
            throws Exception {
        List<Filter> filtersToDelete = new ArrayList<>();

        for (Filter filter : databaseManager.getDatabaseFilterManager().getFilters().call()) {
            if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
                continue;
            }

            for (String uniqueId : filter.boards.split(",")) {
                String[] split = uniqueId.split(":");
                if (split.length == 2 && Integer.parseInt(split[0]) == site.id()) {
                    filtersToDelete.add(filter);
                    break;
                }
            }
        }

        databaseManager.getDatabaseFilterManager().deleteFilters(filtersToDelete).call();
    }

    public class Sites
            extends Observable {
        private List<Site> sites = Collections.unmodifiableList(new ArrayList<>());
        private SparseArray<Site> sitesById = new SparseArray<>();

        public Site forId(int id) {
            Site s = sitesById.get(id);
            if (s == null) {
                throw new IllegalArgumentException("No site with id (" + id + ")");
            }
            return s;
        }

        public List<Site> getAll() {
            return new ArrayList<>(sites);
        }

        public List<Site> getAllInOrder() {
            Map<Integer, Integer> ordering = getOrdering();

            List<Site> ordered = new ArrayList<>(sites);
            Collections.sort(ordered, (lhs, rhs) -> ordering.get(lhs.id()) - ordering.get(rhs.id()));

            return ordered;
        }

        private void addAll(List<Site> all) {
            List<Site> copy = new ArrayList<>(sites);
            copy.addAll(all);
            resetSites(copy);
            setChanged();
        }

        private void add(Site site) {
            List<Site> copy = new ArrayList<>(sites);
            copy.add(site);
            resetSites(copy);
            setChanged();
        }

        // We don't keep the order ourselves here, that's the task of listeners. Do notify the
        // listeners.
        private void wasReordered() {
            setChanged();
        }

        private void resetSites(List<Site> newSites) {
            sites = Collections.unmodifiableList(newSites);
            SparseArray<Site> byId = new SparseArray<>(newSites.size());
            for (Site newSite : newSites) {
                byId.put(newSite.id(), newSite);
            }
            sitesById = byId;
        }
    }

    private class SiteConfigSettingsHolder {
        Site site;
        SiteConfig config;
        JsonSettings settings;

        public SiteConfigSettingsHolder(Site site, SiteConfig config, JsonSettings settings) {
            this.site = site;
            this.config = config;
            this.settings = settings;
        }
    }

    public interface SitesInitializationListener {
        void onSitesInitialized();
        void onFailedToInitialize(Throwable error);
    }
}
