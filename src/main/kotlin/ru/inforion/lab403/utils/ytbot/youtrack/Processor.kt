package ru.inforion.lab403.utils.ytbot.youtrack

import com.google.gson.internal.LinkedTreeMap
import ru.inforion.lab403.common.extensions.emptyString
import ru.inforion.lab403.common.extensions.stretch
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.*
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.*
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class Processor(val youtrack: Youtrack, val lastUpdateTimestamp: Long, val appConfig: ApplicationConfig) {
    companion object {
        val log = logger()

        private fun crop(string: String, size: Int) =
            if (string.length <= size) string else "${string.stretch(size)}..."

        private fun escapeMarkdown(string: String) = string
            .replace("_", "\\_")
            .replace("*", "\\*")
    }

    private fun processAddedRemoved(activity: Activity, block: (ArrayList<LinkedTreeMap<String, String>>) -> String?): String {
        log.finer { activity.added.toString() }

        @Suppress("UNCHECKED_CAST")
        val added = activity.added as ArrayList<LinkedTreeMap<String, String>>

        if (added.isNotEmpty()) {
            val result = block(added)
            return if (appConfig.omitEmptyFields && result == null) emptyString else "Added $result"
        }

        log.finer { activity.removed.toString() }

        @Suppress("UNCHECKED_CAST")
        val removed = activity.removed as ArrayList<LinkedTreeMap<String, String>>

        if (removed.isNotEmpty()) {
            val result = block(removed)
            return if (appConfig.omitEmptyFields && result == null) emptyString else "Removed $result"
        }

        throw RuntimeException("Added or Removed must be not empty!")
    }

    private fun processInternalCustomFieldMapValue(presentation: String, fields: Map<String, String>): String {
        val data = fields.getValue("name")
        return when (presentation) {
            in appConfig.userCustomFields -> {
                val login = fields.getValue("login")
                val ringId = fields["ringId"]
                youtrack.tagUsername(login, ringId, appConfig.users)
            }
            // Add hash tag to specific fields
            in appConfig.taggedCustomFields -> {
                "#${escapeMarkdown(data).removeChars(' ', '-', '.')}"
            }
            else -> escapeMarkdown(data)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processInternalCustomField(presentation: String, value: Any?) = runCatching {
        val second = when (value) {
            is Map<*, *> -> processInternalCustomFieldMapValue(presentation, value as Map<String, String>)

            is List<*> -> {
                val result = value.joinToString {
                    processInternalCustomFieldMapValue(presentation, it as Map<String, String>)
                }

                if (appConfig.omitEmptyFields && result.isBlank()) null else result
            }

            null -> if (appConfig.omitEmptyFields) null else "-"

            else -> {
                log.severe { "$presentation: can't process $value" }
                value.toString()
            }
        }
        if (second == null) null else "$presentation: $second"
    }.onFailure {
        log.severe { "Can't parse field '$presentation' with '$value' -> $it" }
    }.getOrElse { "$presentation: $value" }

    private fun processInternalDescription(description: String?) =
        if (description != null) escapeMarkdown(crop(description, appConfig.descriptionMaxChars)) else emptyString

    private fun processIssueCreatedActivity(project: Project, issue: Issue, activity: Activity): String {
        val stringFields = issue.fields
            .filter { it.name !in appConfig.userCustomFields }
            .mapNotNull { processInternalCustomField(it.name, it.value) }
            .joinToString("\n ")
        val vDesc = processInternalDescription(issue.description)
        val vvDesc = if (vDesc.isNotBlank()) "\n$vDesc" else ""
        val summary = escapeMarkdown(issue.summary)
        return "$summary$vvDesc\n $stringFields"
    }

    private fun processLinkActivity(project: Project, issue: Issue, activity: Activity) =
        processAddedRemoved(activity) {
            val idReadable = it[0]["idReadable"]!!
            val summary = it[0]["summary"]!!

            val urlOther = youtrack.tagId(idReadable)
            "${activity.field.presentation}: $urlOther $summary"
        }

    private fun processCommentActivity(project: Project, issue: Issue, activity: Activity) =
        processAddedRemoved(activity) {
            var text = it[0]["text"]!!
            if (appConfig.users != null) {
                appConfig.users.keys.forEach {
                    val name = "@$it"
                    if (name in text) {
                        val tagUsername = youtrack.tagUsername(it, null, appConfig.users)
                        text = text.replace(name, tagUsername)
                    }
                }
            }
            text
        }

    private fun processVcsChangesActivity(project: Project, issue: Issue, activity: Activity): String {
        @Suppress("UNCHECKED_CAST")
        val added = activity.added as ArrayList<LinkedTreeMap<String, String>>

        val text = added[0]["text"]!!.trim()
        val urls = added[0]["urls"]

        val tagged = text.replace("#${project.shortName}-", "#${project.shortName}")

        return if (urls != null) "Added commit: [$tagged]($urls)" else "Added commit: $tagged"
    }

    private fun processIssueResolved(project: Project, issue: Issue, activity: Activity) = "#${activity.targetMember}"

    private fun processDescription(project: Project, issue: Issue, activity: Activity): String {
        return processInternalDescription(activity.added as String?)
    }

    private fun processAttachments(project: Project, issue: Issue, activity: Activity) =
        processAddedRemoved(activity) {
            val name = it[0]["name"]!!
            val url = it[0]["url"]
            val mimeType = it[0]["mimeType"] ?: ""

            val result = if (url != null) "[$name](${youtrack.baseUrl}$url) $mimeType" else "$name $mimeType"

            "${activity.field.presentation}: ${result.trim()}"
        }

    private fun processCustomField(project: Project, issue: Issue, activity: Activity): String {
        val presentation = activity.field.presentation
        return if (activity.added is ArrayList<*> || activity.removed is ArrayList<*>) {
            processAddedRemoved(activity) { processInternalCustomField(presentation, it[0]) }
        } else {
            "$presentation added: ${activity.added} removed: ${activity.removed}"
        }
    }

    private fun processSummary(project: Project, issue: Issue, activity: Activity): String {
        return "${activity.field.presentation} changed: ${activity.added}"
    }

    private fun processUnknownActivity(project: Project, issue: Issue, activity: Activity): String {
        log.severe { "Don't known what to do with ${activity.category.id}!" }
        return "Something has been done..."
    }

    private fun getProcessorBy(category: Category) = when (category.id) {
        CategoryId.IssueCreatedCategory -> this::processIssueCreatedActivity
        CategoryId.LinksCategory -> this::processLinkActivity
        CategoryId.CommentsCategory -> this::processCommentActivity
        CategoryId.VcsChangeCategory -> this::processVcsChangesActivity
        CategoryId.IssueResolvedCategory -> this::processIssueResolved
        CategoryId.DescriptionCategory -> this::processDescription
        CategoryId.AttachmentsCategory -> this::processAttachments
        CategoryId.CustomFieldCategory -> this::processCustomField
        CategoryId.SummaryCategory -> this::processSummary

        else -> this::processUnknownActivity
    }

    private fun updateTimestamp(activityTimestamp: Long) {
        if (appConfig.loadTimestamp() < activityTimestamp) {
            log.info {
                val date = Youtrack.makeTimedate(activityTimestamp)
                "Updating timestamp = $activityTimestamp [$date]"
            }
            appConfig.saveTimestamp(activityTimestamp)
        }
    }

    private fun processActivitiesByCategory(
        issue: Issue,
        date: Date,
        project: Project,
        author: User,
        category: Category,
        activities: Collection<Activity>,
        block: (String) -> Unit
    ) {
        val processor = getProcessorBy(category)

        val sdfCoarse = SimpleDateFormat("dd.MM.YYYY")
        val sdfFine = SimpleDateFormat("HH:mm:ss")

        var timestamp: Long = 0

//        val completeIssue = loadIssueComplete(issue.id)

        val result = buildString {
            val dateString = sdfCoarse.format(date)
            val tagId = youtrack.tagId(issue.idReadable)
            val tagAuthor = youtrack.tagUsername(author.login, author.ringId, appConfig.users)
            val tagCategory = youtrack.tagCategory(category.id)
            val summary = escapeMarkdown(crop(issue.summary, appConfig.descriptionMaxChars))

            append("$dateString $tagId $summary $tagCategory")
            append("\n- Author: $tagAuthor")

            // Add all user fields
            issue.fields
                .filter { it.name in appConfig.userCustomFields }
                .mapNotNull { processInternalCustomField(it.name, it.value) }
                .forEach { append("\n- $it") }

            timestamp = activities.map {
                val time = sdfFine.format(Date(it.timestamp))
                val activityPermlink = youtrack.activityPermlink(issue.idReadable, it.id, time)

                val activityText = runCatching {
                    processor(project, issue, it)
                }.onFailure {
                    log.severe { "${it.message}\n${it.stackTraceToString()}" }
                }.getOrNull() ?: ""

                if (!appConfig.omitEmptyFields || activityText.isNotBlank())
                    append("\n- $activityPermlink $activityText")

                it.timestamp
            }.maxOrNull() ?: 0
        }

        if (appConfig.isCategoryActive(category.id))
            block(result)

        updateTimestamp(timestamp)
    }

    private fun processIssue(issue: Issue, project: Project, block: (String) -> Unit) {
        log.info {
            val datetime = Youtrack.makeTimedate(issue.updated)
            "Request issue activities: ${issue.id} ${issue.updated} [$datetime] $ ${issue.idReadable}"
        }

        val activitiesPage = youtrack.activitiesPage(
            issue,
            fields(
                ActivitiesPage::activities.with(
                    Activity::id,
                    Activity::added.with(
                        IssueAttachment::name,
                        IssueAttachment::mimeType,
                        IssueAttachment::url,
                        IssueComment::text,
                        Issue::idReadable,
                        Issue::summary,
                        User::email,
                        User::ringId,
                        User::login
                    ),
                    Activity::removed.with(
                        IssueAttachment::name,
                        IssueAttachment::mimeType,
                        IssueAttachment::url,
                        IssueComment::text,
                        Issue::idReadable,
                        Issue::summary,
                        User::email,
                        User::ringId,
                        User::login
                    ),
                    Activity::timestamp,
                    Activity::targetMember,
                    Activity::author.with(
                        User::fullName,
                        User::name,
                        User::email,
                        User::ringId,
                        User::login
                    ),
                    Activity::category.with(Category::id),
                    Activity::field.with(
                        ActivityField::presentation
                    ),
                    Activity::target.with(
                        Issue::fields.with(
                            CustomField::name
                        ),
                        Issue::customFields.with(
                            CustomField::name
                        ),
                        Issue::idReadable,
                        Issue::summary,
                        Issue::id
                    )
                )
            ),
            enumValuesCollection()  // filtered in lambda due to timestamp update required
        )

        val allIssueActivities = activitiesPage.activities.filter { it.timestamp > lastUpdateTimestamp }

        log.info {
            val datetime = Youtrack.makeTimedate(issue.updated)
            "Processing issue activities: ${issue.id} ${issue.updated} [$datetime] ${issue.idReadable} count=${allIssueActivities.size}"
        }

        val minutesGroupInterval = appConfig.minutesGroupInterval
        val timeGroupInterval = 60000 * minutesGroupInterval

        allIssueActivities
            .filter { it.timestamp > lastUpdateTimestamp }
            .groupSeriesBy { it.author }
            .forEach { (author, authorActivities) ->
                authorActivities
                    .groupBy { Date(it.timestamp / timeGroupInterval * timeGroupInterval) }
                    .forEach { (date, dateActivities) ->
                        dateActivities
                            .groupBy { it.category }
                            .forEach { (category, activities) ->
                                processActivitiesByCategory(
                                    issue,
                                    date,
                                    project,
                                    author,
                                    category,
                                    activities,
                                    block
                                )
                            }
                    }
            }
    }

    fun processProject(project: Project, block: (String) -> Unit) {
        val start = Youtrack.makeTimedate(lastUpdateTimestamp)

        log.finer { "Processing project: ${project.id} ${project.name} ${project.shortName} from $start" }

        val issues = youtrack.issues(
            project,
            fields(
                Issue::id,
                Issue::idReadable,
                Issue::updated,
                Issue::summary,
                Issue::description,
                Issue::fields.with("name", "value(name,login,ringId)")
            ),
            query = "updated: $start .. *"  // star is open range
        )

        val filteredIssues = if (appConfig.filterIssues == null) issues else {
            log.warning { "Issues filter specified: ${appConfig.filterIssues}" }
            issues.filter { it.idReadable in appConfig.filterIssues }
        }

        filteredIssues
            .filter { it.updated > lastUpdateTimestamp }  // may already be processed due to milliseconds
            .forEach { processIssue(it, project, block) }
    }

    init {
        assert(lastUpdateTimestamp >= 0)
    }
}