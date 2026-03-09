package com.craftsmanbro.fulcraft.plugins.reporting.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TasksSnapshotTest {

  @Test
  void empty_ShouldReturnEmptySnapshot() {
    TasksSnapshot snapshot = TasksSnapshot.empty();

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.tasks()).isEmpty();
    assertThat(snapshot.isEmpty()).isTrue();
  }

  @Test
  void isEmpty_ShouldReturnTrue_WhenTasksAreEmpty() {
    TasksSnapshot snapshot = new TasksSnapshot(List.of());
    assertThat(snapshot.isEmpty()).isTrue();
  }

  @Test
  void isEmpty_ShouldReturnFalse_WhenTasksAreNotEmpty() {
    TaskRecord task = new TaskRecord();
    TasksSnapshot snapshot = new TasksSnapshot(List.of(task));

    assertThat(snapshot.isEmpty()).isFalse();
  }

  @Test
  void constructor_ShouldAssignFieldsCorrectly() {
    List<TaskRecord> tasks = new ArrayList<>();
    tasks.add(new TaskRecord());

    TasksSnapshot snapshot = new TasksSnapshot(tasks);

    assertThat(snapshot.tasks()).isSameAs(tasks);
  }

  @Test
  void empty_ShouldExposeImmutableTaskList() {
    TasksSnapshot snapshot = TasksSnapshot.empty();

    assertThatThrownBy(() -> snapshot.tasks().add(new TaskRecord()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isEmpty_ShouldThrowNullPointerException_WhenTasksAreNull() {
    TasksSnapshot snapshot = new TasksSnapshot(null);

    assertThat(snapshot.tasks()).isNull();
    assertThatThrownBy(snapshot::isEmpty).isInstanceOf(NullPointerException.class);
  }
}
