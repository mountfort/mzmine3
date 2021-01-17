/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.util.javafx.groupablelistview;

import io.github.mzmine.util.javafx.DraggableListCell;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * Class designed to be used as a cell of {@link GroupableListView}.
 *
 * @param <T> type of the cell content
 */
public class GroupableListViewCell<T> extends
    DraggableListCell<GroupableListViewEntity> {

  private static final int INDENT = 20;
  private final String POSTFIX = "file";

  private final Text expandButton = new Text("▼");
  private final Text hiddenButton = new Text("▶");

  private final TextField renameTextField = new TextField();
  private Node renameSavedGraphic;

  public GroupableListViewCell(MenuItem groupUngroupMenuItem) {
    setEditable(true);

    // Setup renaming text fields
    renameTextField.setOnAction(event -> commitEdit(getItem()));
    renameTextField.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        cancelEdit();
      }
    });

    // Setup group headers expanding/hiding
    expandButton.setOnMouseClicked(event -> {
      getListView().getItems().removeAll(((GroupableListView<T>) getListView())
          .getGroupItems((GroupEntity) getItem()));
      setGraphic(hiddenButton);
      ((GroupEntity) getItem()).invertState();
    });
    hiddenButton.setOnMouseClicked(event -> {
      getListView().getItems().addAll(getIndex() + 1, ((GroupableListView<T>) getListView())
          .getGroupItems((GroupEntity) getItem()));
      setGraphic(expandButton);
      ((GroupEntity) getItem()).invertState();
    });

    // Setup grouping context menu item
    Platform.runLater(() -> {
      getListView().getSelectionModel().getSelectedItems().addListener(new ListChangeListener<GroupableListViewEntity>() {
        @Override
        public void onChanged(Change<? extends GroupableListViewEntity> change) {
          String postfix = POSTFIX;
          if (getGroupableListView().getSelectedValues().size() > 1) {
            postfix += "s";
          }

          boolean groupedSelected = false;
          boolean ungroupedSelected = false;

          for (GroupableListViewEntity item : change.getList()) {
            if ((item instanceof ValueEntity && ((ValueEntity<?>) item).isGrouped())
                || item instanceof GroupEntity) {
              groupedSelected = true;
            } else if (item instanceof ValueEntity && !((ValueEntity<?>) item).isGrouped()) {
              ungroupedSelected = true;
            }
          }

          if (groupedSelected && ungroupedSelected) {
            groupUngroupMenuItem.setText("Group/Ungroup " + postfix);
            groupUngroupMenuItem.setDisable(true);
          } else if (ungroupedSelected) {
            groupUngroupMenuItem.setText("Group " + postfix);
            groupUngroupMenuItem.setDisable(false);
          } else {
            groupUngroupMenuItem.setText("Ungroup " + postfix);
            groupUngroupMenuItem.setDisable(false);
          }
        }
      });
    });
  }

  @Override
  protected void updateItem(GroupableListViewEntity item, boolean empty) {
    super.updateItem(item, empty);
    if (empty || (item == null)) {
      setText("");
      setGraphic(null);
      return;
    }

    setText(item.toString());

    // Shift item to the right, if it's grouped
    if (item instanceof ValueEntity && ((ValueEntity<T>) item).isGrouped()) {
      setStyle("-fx-padding: 3 3 3 " + INDENT + ";");
    } else {
      setStyle("-fx-padding: 3 3 3 5;");
    }

    // Set expand/hide button as item's graphic. if it's group header
    if (item instanceof GroupEntity) {
      setGraphic(((GroupEntity) item).isExpanded() ? expandButton : hiddenButton);
      textFillProperty().unbind();
      textFillProperty().setValue(Color.BLACK);
    }
  }

  @Override
  public void startEdit() {
    setEditable(true);
    if (!isEditable() || !getListView().isEditable() || getItem() == null) {
      return;
    }
    super.startEdit();
    renameSavedGraphic = getGraphic();

    // Create HBox with renaming text field
    renameTextField.setText(getItem().toString());
    setText(null);
    HBox hbox = new HBox();
    hbox.getChildren().add(getGraphic());
    hbox.getChildren().add(renameTextField);
    hbox.setAlignment(Pos.CENTER_LEFT);
    setGraphic(hbox);

    renameTextField.selectAll();
    renameTextField.requestFocus();
  }

  @Override
  public void cancelEdit() {
    if (getItem() == null) {
      return;
    }
    super.cancelEdit();

    setText(getItem().toString());
    setGraphic(renameSavedGraphic);
    getListView().setEditable(false);
  }

  @Override
  public void commitEdit(GroupableListViewEntity item) {
    if (item == null) {
      return;
    }
    super.commitEdit(item);

    if (item instanceof GroupEntity) {
      getGroupableListView().renameGroup((GroupEntity) item, renameTextField.getText());
    }
    setGraphic(renameSavedGraphic);
    setText(renameTextField.getText());
    getListView().setEditable(false);

    getListView().getSelectionModel().clearSelection();
    getListView().getSelectionModel().select(getIndex());
  }

  /**
   * Method extending {@link DraggableListCell#dragDroppedAction} to define drag and drop behaviour
   * of {@link GroupableListViewCell}s.
   *
   * @param draggedIdx index of the dragged item
   * @param draggedToIdx new index for the dragged item
   */
  @Override
  protected void dragDroppedAction(int draggedIdx, int draggedToIdx) {
    GroupableListViewEntity draggedItem = getListView().getItems().get(draggedIdx);
    GroupableListViewEntity draggedToItem = getListView().getItems().get(draggedToIdx);

    // If item is dragged to selected item, do nothing
    if (getListView().getSelectionModel().getSelectedItems().contains(draggedToItem)) {
      return;
    }

    // If groups' headers selected along with grouped element, do nothing
    if (getGroupableListView().anyGroupSelected()
        && (draggedToItem instanceof ValueEntity && (((ValueEntity<?>) draggedToItem).isGrouped())
        || (draggedItem instanceof ValueEntity && ((ValueEntity<?>) draggedItem).isGrouped()))) {
      return;
    }

    // Place selected elements to the new index
    super.dragDroppedAction(draggedIdx, getListView().getItems().indexOf(draggedToItem));

    // Replace expanded groups' elements to their new positions,
    // save selected values and remove them from groups.
    List<ValueEntity<T>> selectedValues = new ArrayList<>();
    for (GroupableListViewEntity selectedItem : getListView().getSelectionModel().getSelectedItems()) {
      if (selectedItem instanceof GroupEntity && ((GroupEntity) selectedItem).isExpanded()) {
        getListView().getItems().removeAll(
            getGroupableListView().getGroupItems((GroupEntity) selectedItem));
        getListView().getItems().addAll(getListView().getItems().indexOf(selectedItem) + 1,
            getGroupableListView().getGroupItems((GroupEntity) selectedItem));
      } else if (selectedItem instanceof ValueEntity) {
        getGroupableListView().removeFromGroup((ValueEntity<T>) selectedItem);
        selectedValues.add((ValueEntity<T>) selectedItem);
      }
    }

    // If drag target item is group, replace it's items to their new positions
    if (draggedToItem instanceof GroupEntity && ((GroupEntity) draggedToItem).isExpanded()) {
      getListView().getItems().removeAll(
          getGroupableListView().getGroupItems((GroupEntity) draggedToItem));
      getListView().getItems().addAll(getListView().getItems().indexOf(draggedToItem) + 1,
          getGroupableListView().getGroupItems((GroupEntity) draggedToItem));
    }

    // If items are dragged inside the group, add them to this group
    if (draggedToItem instanceof ValueEntity && ((ValueEntity<?>) draggedToItem).isGrouped()) {
      GroupEntity group = ((ValueEntity<?>) draggedToItem).getGroup();

      // Calculate new index relative to group and add items to that index
      int groupedItemsIdx = getGroupableListView().getGroupItems(group).indexOf(draggedToItem);
      if (draggedToIdx > draggedIdx) {
        groupedItemsIdx++;
      }
      getGroupableListView().addToGroup(group, groupedItemsIdx, selectedValues);
    }

    // Update selection
    int newDraggedItemIdx = getGroupableListView().getItems().indexOf(draggedItem);
    int selectedItemsSize = getListView().getSelectionModel().getSelectedItems().size();
    getListView().getSelectionModel().clearSelection();
    getListView().getSelectionModel().selectRange(newDraggedItemIdx,
        newDraggedItemIdx + selectedItemsSize);

  }

  public GroupableListView<T> getGroupableListView() {
    return (GroupableListView<T>) getListView();
  }
}
