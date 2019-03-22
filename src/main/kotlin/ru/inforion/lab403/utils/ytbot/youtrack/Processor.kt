package ru.inforion.lab403.utils.ytbot.youtrack

import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.youtrack.categories.*
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

class Processor(val youtrack: Youtrack) {
    companion object {
        val log = logger(Level.FINE)
    }

    private val activityProcessors = mapOf(
        CategoryId.IssueCreatedCategory to IssueCreatedCategoryProcessor(youtrack),
        CategoryId.LinksCategory to LinkActivityCategoryProcessor(youtrack),
        CategoryId.CommentsCategory to CommentsActivityCategoryProcessor(youtrack)
    )

    private val unknownActivityProcessor = UnknownActivityCategoryProcessor(youtrack)

    private fun processActivitiesByCategory(
        date: Date,
        author: User,
        category: Category,
        activities: Collection<Activity>,
        block: (String) -> Unit
    ) {
        val processor = activityProcessors[category.id] ?: unknownActivityProcessor

        val sdfCoarse = SimpleDateFormat("dd.MM.YYYY H:m")
        val sdfFine = SimpleDateFormat("H:m:s")

        val result = buildString {
            val coarseDate = sdfCoarse.format(date)
            append("*${author.fullName}* at $coarseDate has ${processor.description()}:")
            activities.forEach {
                val fineTime = sdfFine.format(Date(it.timestamp))
                append("\n - [$fineTime] ${processor.stringify(it)}")
            }
        }

        block(result)
    }

    fun processProject(project: Project, block: (String) -> Unit) {
        val issues = youtrack.issues(project, fields(Issue::id, Issue::idReadable))
        val issue = issues.first { it.idReadable == "KC-1243" }

        val activitiesPage = youtrack.activitiesPage(
            issue,
            fields(
                ActivitiesPage::activities.with(
                    Activity::added.with(
                        IssueComment::text,
                        Issue::idReadable,
                        Issue::summary
                    ),
                    Activity::timestamp,
                    Activity::author.with(
                        User::fullName
                    ),
                    Activity::category.with(Category::id),
                    Activity::field.with(
                        ActivityField::presentation
                    ),
                    Activity::target.with(
                        Issue::fields.with(
                            CustomField::name
                        ),
                        Issue::idReadable,
                        Issue::summary
                    )
                )
            ),
            CategoryId.values().toSet()
        )

        val lastUpdateTimestamp = 0

        val allIssueActivities = activitiesPage.activities

        val minutesGroupInterval = 5
        val timeGroupInterval = 60000 * minutesGroupInterval

        allIssueActivities.forEach {
            println("${it.target.idReadable} ${it.category}")
        }

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
                                processActivitiesByCategory(date, author, category, activities, block)
                            }
                    }
            }
    }
}