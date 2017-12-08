package be.kuleuven.proman.repositories

import be.kuleuven.proman.models.TodoEntry

object TodoEntryRepository {
  private var id: Long = 0
  private var entries: Seq[TodoEntry] = Seq()

  private var state: Long = 0
  private var state_changes: Map[Long, Long] = Map()

  private def nextID: Long = { id += 1; id }
  private def nextState: Long = { state += 1; state }

  def create(project_id: Long, name: String, is_done: Boolean=false): TodoEntry = {
    val todo = new TodoEntry(nextID, project_id, name, is_done)
    this.entries +:= todo
    this.state_changes += (nextState -> todo.id)
    todo
  }

  def all(): Seq[TodoEntry] =
    this.entries

  def allForProject(project_id: Long): Seq[TodoEntry] =
    this.entries.filter(_.project_id == project_id).sortWith(_.timestamp > _.timestamp)

  def find(id: Long): TodoEntry =
    this.entries.find(_.id == id).orNull

  def update(id: Long, todo: TodoEntry): TodoEntry = {
    this.entries = this.entries.updated(this.entries.indexWhere(_.id == id), todo)
    this.state_changes += (nextState -> todo.id)
    todo
  }

  def getState: Long =
    this.state

  def allUpdatedSinceState(project_id: Long, state: Long): List[TodoEntry] =
    this.state_changes.filterKeys(key => key > state).values
      .toList.distinct.map(id => this.find(id))
      .filter(todo => todo.project_id == project_id)
      .sortWith((p1, p2) => p1.id > p2.id)
}