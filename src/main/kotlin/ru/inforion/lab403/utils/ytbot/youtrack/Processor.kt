package ru.inforion.lab403.utils.ytbot.youtrack

import com.google.gson.internal.LinkedTreeMap
import ru.inforion.lab403.common.extensions.stretch
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.Application
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.*
import java.io.File
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import kotlin.collections.ArrayList

class Processor(val youtrack: Youtrack, val lastUpdateTimestamp: Long, val appConfig: ApplicationConfig) {
    companion object {
        val log = logger(Level.FINE)
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

    private fun processIssueCreatedActivity(project: Project, activity: Activity): String {
//        activity.target.customFields.forEach { println(it.name) }
        return activity.target.summary
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

        return if (urls != null)
            "Added commit: [$tagged]($urls)"
        else
            "Added commit: $tagged"
    }

    private fun processIssueResolved(project: Project, activity: Activity) = "#${activity.targetMember}"

    private fun processDescription(project: Project, activity: Activity): String {
        val description = activity.added as String
        return "${description.stretch(appConfig.descriptionMaxChars)}..."
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

        if (activity.added is ArrayList<*> || activity.removed is ArrayList<*>) {
            return processAddedRemoved(activity) {
                val name = it[0]["name"]!!

                if (presentation == appConfig.assigneeFieldName) {
                    val login = it[0]["login"]!!
                    val ringId = it[0]["ringId"]!!
                    youtrack.tagUsername(login, ringId, appConfig.users)
                } else {

                    // Add hash tag to some fields
                    val tag = if (presentation in appConfig.taggedCustomFields) "#" else ""

                    "$tag$name"
                }
            }
        } else {
            return "$presentation -> Added: ${activity.added} Removed: ${activity.removed}"
        }
    }

    private fun processUnknownActivity(project: Project, activity: Activity) = "Something has been done..."

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
        target: Issue,
        date: Date,
        project: Project,
        author: User,
        category: Category,
        activities: Collection<Activity>,
        block: (String, Long) -> Unit
    ) {
        val processor = getProcessorBy(category)

        val sdfCoarse = SimpleDateFormat("dd.MM.YYYY HH:mm")
        val sdfFine = SimpleDateFormat("HH:mm:ss")

        var timestamp: Long = 0

        val result = buildString {
            val coarseDate = sdfCoarse.format(date)
            val tagId = youtrack.tagId(target.idReadable)
            val tagName = youtrack.tagUsername(author.login, author.ringId, appConfig.users)
            val tagActivity = youtrack.tagActivity(category.id)
            append("$coarseDate $tagId $tagName $tagActivity")
            timestamp = activities.map {
                val fineTime = sdfFine.format(Date(it.timestamp))
                append("\n - [$fineTime] ${processor(project, it)}")
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
                        Issue::summary
                    )
                )
            ),
            CategoryId.values().toSet()
        )

        val allIssueActivities = activitiesPage.activities

        val minutesGroupInterval = appConfig.minutesGroupInterval
        val timeGroupInterval = 60000 * minutesGroupInterval

        allIssueActivities
//            .filter { it.category.id == CategoryId.CommentsCategory }
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
        log.info { "Processing project: ${project.id} ${project.name} ${project.shortName}" }

        val issues = youtrack.issues(
            project,
            fields(
                Issue::id,
                Issue::idReadable,
                Issue::updated,
                Issue::summary
            )
        )

        issues
            .filter { it.updated > lastUpdateTimestamp }
//            .filter { it.idReadable == "KC-1243" }
//            .filter { it.idReadable == "KC-1109" }
            .filter { it.idReadable == "KC-1250" }
            .forEach { processIssue(it, project, block) }
    }
}