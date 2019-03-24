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
import java.util.logging.Level
import kotlin.collections.ArrayList

class Processor(val youtrack: Youtrack, val lastUpdateTimestamp: Long, val appConfig: ApplicationConfig) {
    companion object {
        val log = logger(Level.FINE)

        private fun crop(string: String, size: Int) =
            if (string.length <= size) string else "${string.stretch(size)}..."
    }

    private fun processAddedRemoved(activity: Activity, block: (ArrayList<LinkedTreeMap<String, String>>) -> String): String {
        log.finer { activity.added.toString() }

        @Suppress("UNCHECKED_CAST")
        val added = activity.added as ArrayList<LinkedTreeMap<String, String>>

        if (added.isNotEmpty())
            return "Added ${activity.field.presentation}: ${block(added)}"

        log.finer { activity.removed.toString() }

        @Suppress("UNCHECKED_CAST")
        val removed = activity.removed as ArrayList<LinkedTreeMap<String, String>>

        if (removed.isNotEmpty())
            return "Removed ${activity.field.presentation}: ${block(removed)}"

        throw RuntimeException("Added or Removed must be not empty!")
    }

    private fun processInternalCustomField(presentation: String, value: LinkedTreeMap<String, String>?): String {
        if (value == null)
            return "$presentation: -"

        val data = value["name"]!!
        val second = when (presentation) {
            in appConfig.userCustomFields -> {
                val login = value["login"]!!
                val ringId = value["ringId"]
                youtrack.tagUsername(login, ringId, appConfig.users)
            }
            // Add hash tag to specific fields
            in appConfig.taggedCustomFields -> {
                "#${data.removeChars(' ', '-', '.')}"
            }
            else -> data
        }
        return "$presentation: $second"
    }

    private fun processInternalDescription(description: String?) =
        if (description != null) crop(description, appConfig.descriptionMaxChars) else emptyString

    private fun processIssueCreatedActivity(project: Project, activity: Activity): String {
        val fields = fields(
            Issue::id,
            Issue::summary,
            Issue::description,
            Issue::fields.with("name", "value(name,login,ringId)")
        )
        val issue = youtrack.issue(activity.target.id, fields)
        val stringFields = issue.fields.joinToString("\n ") {
            @Suppress("UNCHECKED_CAST")
            val value = it.value as LinkedTreeMap<String, String>?
            processInternalCustomField(it.name, value)
        }
        val vDesc = processInternalDescription(issue.description)
        val vvDesc = if (vDesc.isNotBlank()) "\n$vDesc" else ""
        return "${issue.summary}$vvDesc\n $stringFields"
    }

    private fun processLinkActivity(project: Project, activity: Activity) = processAddedRemoved(activity) {
        val idReadable = it[0]["idReadable"]!!
        val summary = it[0]["summary"]!!

        val urlOther = youtrack.tagId(idReadable)
        "$urlOther $summary"
    }

    private fun processCommentActivity(project: Project, activity: Activity) = processAddedRemoved(activity) {
        it[0]["text"]!!
    }

    private fun processVcsChangesActivity(project: Project, activity: Activity): String {
        @Suppress("UNCHECKED_CAST")
        val added = activity.added as ArrayList<LinkedTreeMap<String, String>>

        val text = added[0]["text"]!!.trim()
        val urls = added[0]["urls"]

        val tagged = text.replace("#${project.shortName}-", "#${project.shortName}")

        return if (urls != null) "Added commit: [$tagged]($urls)" else "Added commit: $tagged"
    }

    private fun processIssueResolved(project: Project, activity: Activity) = "#${activity.targetMember}"

    private fun processDescription(project: Project, activity: Activity): String {
        return processInternalDescription(activity.added as String?)
    }

    private fun processAttachments(project: Project, activity: Activity) = processAddedRemoved(activity) {
        val name = it[0]["name"]!!
        val url = it[0]["url"]
        val mimeType = it[0]["mimeType"] ?: ""

        val result = if (url != null) "[$name](${youtrack.baseUrl}$url) $mimeType" else "$name $mimeType"

        result.trim()
    }

    private fun processCustomField(project: Project, activity: Activity): String {
        val presentation = activity.field.presentation
        return if (activity.added is ArrayList<*> || activity.removed is ArrayList<*>) {
            processAddedRemoved(activity) { processInternalCustomField(presentation, it[0]) }
        } else {
            "$presentation -> Added: ${activity.added} Removed: ${activity.removed}"
        }
    }

    private fun processUnknownActivity(project: Project, activity: Activity): String {
        log.severe { "Don't what to do with ${activity.category.id}!" }
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

        else -> this::processUnknownActivity
    }

    private fun processActivitiesByCategory(
        issue: Issue,
        date: Date,
        project: Project,
        author: User,
        category: Category,
        activities: Collection<Activity>,
        block: (String, Long) -> Unit
    ) {
        val processor = getProcessorBy(category)

        val sdfCoarse = SimpleDateFormat("dd.MM.YYYY")
        val sdfFine = SimpleDateFormat("HH:mm:ss")

        var timestamp: Long = 0

        val result = buildString {
            val coarseDate = sdfCoarse.format(date)
            val tagId = youtrack.tagId(issue.idReadable)
            val tagUsername = youtrack.tagUsername(author.login, author.ringId, appConfig.users)
            val tagCategory = youtrack.tagCategory(category.id)
            val summary = crop(issue.summary, appConfig.descriptionMaxChars)
            append("$coarseDate $tagId $summary $tagUsername $tagCategory")
            timestamp = activities.map {
                val fineTime = sdfFine.format(Date(it.timestamp))
                val activityPermlink = youtrack.activityPermlink(issue.idReadable, it.id, fineTime)
                append("\n- $activityPermlink ${processor(project, it)}")
                it.timestamp
            }.max() ?: 0
        }

        block(result, timestamp)
    }

    private fun processIssue(issue: Issue, project: Project, block: (String, Long) -> Unit) {
        log.info { "Processing issue: ${issue.id} ${issue.updated} ${issue.idReadable} ${issue.summary}" }

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
            appConfig.activityCategories ?: enumValuesCollection()
        )

        val allIssueActivities = activitiesPage.activities

        val minutesGroupInterval = appConfig.minutesGroupInterval
        val timeGroupInterval = 60000 * minutesGroupInterval

        allIssueActivities
            .filter { it.timestamp > lastUpdateTimestamp }
            .groupSeriesBy { it.author }
            .forEach { (author, authorActivities) ->
                authorActivities
                    .groupBy { Date(it.timestamp / timeGroupInterval * timeGroupInterval) }
                    .forEach { date, dateActivities ->
                        dateActivities
                            .groupBy { it.category }
                            .forEach { category, activities ->
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

    fun processProject(project: Project, block: (String, Long) -> Unit) {
        log.finer { "Processing project: ${project.id} ${project.name} ${project.shortName}" }

        val issues = youtrack.issues(
            project,
            fields(
                Issue::id,
                Issue::idReadable,
                Issue::updated,
                Issue::summary
            )
        )

        val filteredIssues = if (appConfig.filterIssues == null) issues else {
            log.warning { "Issues filter specified: ${appConfig.filterIssues}" }
            issues.filter { it.idReadable in appConfig.filterIssues }
        }

        filteredIssues
            .filter { it.updated > lastUpdateTimestamp }
            .forEach { processIssue(it, project, block) }
    }
}