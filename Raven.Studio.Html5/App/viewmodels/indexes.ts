﻿import getDatabaseStatsCommand = require("commands/getDatabaseStatsCommand");
import viewModelBase = require("viewmodels/viewModelBase");
import index = require("models/index");
import appUrl = require("common/appUrl");
import saveIndexLockModeCommand = require("commands/saveIndexLockModeCommand");
import saveIndexAsPersistentCommand = require("commands/saveIndexAsPersistentCommand");
import deleteIndexesConfirm = require("viewmodels/deleteIndexesConfirm");
import getStoredQueriesCommand = require("commands/getStoredQueriesCommand");
import querySort = require("models/querySort");
import app = require("durandal/app");

class indexes extends viewModelBase {

    indexGroups = ko.observableArray<{ entityName: string; indexes: KnockoutObservableArray<index> }>();
    queryUrl = ko.observable<string>();
    newIndexUrl = appUrl.forCurrentDatabase().newIndex;
    containerSelector = "#indexesContainer";
    recentQueries = ko.observableArray<storedQueryDto>()
    
    activate(args) {
        super.activate(args);

        this.fetchIndexes();
        this.fetchRecentQueries();
        this.queryUrl(appUrl.forQuery(this.activeDatabase(), null));
    }

    attached() {
        // Alt+Minus and Alt+Plus are already setup. Since laptops don't have a dedicated key for plus, we'll also use the equal sign key (co-opted for plus).
        this.createKeyboardShortcut("Alt+=", () => this.expandAll(), this.containerSelector);
    }

    fetchIndexes() {
        new getDatabaseStatsCommand(this.activeDatabase())
            .execute()
            .done((stats: databaseStatisticsDto) => this.processDbStats(stats));
    }

    fetchRecentQueries() {
        new getStoredQueriesCommand(this.activeDatabase())
            .execute()
            .done((doc: storedQueryContainerDto) => this.recentQueries(doc.Queries));
    }

    getRecentQueryUrl(query: storedQueryDto) {
        return appUrl.forQuery(this.activeDatabase(), query.Hash);
    }

    getRecentQuerySortText(query: storedQueryDto) {
        if (query.Sorts.length === 0) {
            return "";
        }
        return query.Sorts
            .map(s => querySort.fromQuerySortString(s))
            .map(s => s.toHumanizedString())
            .reduce((first, second) => first + ", " + second);
    }

    processDbStats(stats: databaseStatisticsDto) {
        stats.Indexes
            .map(i => new index(i))
            .forEach(i => this.putIndexIntoGroups(i));
    }

    putIndexIntoGroups(i: index) {
        if (i.forEntityName.length === 0) {
            this.putIndexIntoGroupNamed(i, "Other");
        } else {
            i.forEntityName.forEach(e => this.putIndexIntoGroupNamed(i, e));
        }
    }

    putIndexIntoGroupNamed(i: index, groupName: string) {
        var group = this.indexGroups.first(g => g.entityName === groupName);
        if (group) {
            group.indexes.push(i);
        } else {
            this.indexGroups.push({ entityName: groupName, indexes: ko.observableArray([i]) });
        }
    }

    collapseAll() {
        $(".index-group-content").collapse('hide');
    }

    expandAll() {
        $(".index-group-content").collapse('show');
    }

    deleteIdleIndexes() {
        var idleIndexes = this.getAllIndexes().filter(i => i.priority && i.priority.indexOf("Idle") !== -1);
        this.promptDeleteIndexes(idleIndexes);
    }

    deleteDisabledIndexes() {
        var abandonedIndexes = this.getAllIndexes().filter(i => i.priority && i.priority.indexOf("Disabled") !== -1);
        this.promptDeleteIndexes(abandonedIndexes);
    }

    deleteAbandonedIndexes() {
        var abandonedIndexes = this.getAllIndexes().filter(i => i.priority && i.priority.indexOf("Abandoned") !== -1);
        this.promptDeleteIndexes(abandonedIndexes);
    }

    deleteAllIndexes() {
        this.promptDeleteIndexes(this.getAllIndexes());
    }

    deleteIndex(i: index) {
        this.promptDeleteIndexes([i]);
    }

    promptDeleteIndexes(indexes: index[]) {
        if (indexes.length > 0) {
            var deleteIndexesVm = new deleteIndexesConfirm(indexes.map(i => i.name), this.activeDatabase());
            app.showDialog(deleteIndexesVm);
            deleteIndexesVm.deleteTask.done(() => this.removeIndexesFromAllGroups(indexes));
        }
    }

    removeIndexesFromAllGroups(indexes: index[]) {
        this.indexGroups().forEach(g => {
            g.indexes.removeAll(indexes);
        });

        // Remove any empty groups.
        this.indexGroups.remove((item: { entityName: string; indexes: KnockoutObservableArray<index> }) => item.indexes().length === 0);
    }

    unlockIndex(i: index) {
        this.updateIndexLockMode(i, "Unlock");
    }

    lockIndex(i: index) { 
        this.updateIndexLockMode(i, "LockedIgnore");
    }

    lockErrorIndex(i: index) {
        this.updateIndexLockMode(i, "LockedError");
    }

    updateIndexLockMode(i: index, newLockMode: string) {
        // The old Studio would prompt if you were sure.
        // However, changing the lock status is easily reversible, so we're skipping the prompt.

        var originalLockMode = i.lockMode();
        if (originalLockMode !== newLockMode) {
            i.lockMode(newLockMode);

            new saveIndexLockModeCommand(i, newLockMode, this.activeDatabase())
                .execute()
                .fail(() => i.lockMode(originalLockMode));
        }
    }

    getAllIndexes(): index[]{
        var all: index[] = [];
        this.indexGroups().forEach(g => all.pushAll(g.indexes()));
        return all.distinct();
    }

    makeIndexPersistent(index: index) {
        new saveIndexAsPersistentCommand(index, this.activeDatabase()).execute();
    }
}

export = indexes;