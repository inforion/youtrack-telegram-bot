package ru.inforion.lab403.utils.ytbot.youtrack

enum class CategoryId(val tag: String) {
    CommentsCategory("Comments"),
    AttachmentsCategory("Attachments"),
    AttachmentRenameCategory("AttachmentRename"),
    CustomFieldCategory("CustomField"),
    DescriptionCategory("Description"),
    IssueCreatedCategory("IssueCreated"),
    IssueResolvedCategory("IssueResolved"),
    LinksCategory("Links"),
    ProjectCategory("Project"),
    PermittedGroupCategory("Permitted"),
    SprintCategory("Sprint"),
    SummaryCategory("Summary"),
    TagsCategory("Tags"),
    VcsChangeCategory("VcsChange")
}
