tasks.register("allDependencies") {
    dependsOn(subprojects.flatMap { project: Project -> project.tasks }.filter { task -> task.name == "dependencies" })
}
