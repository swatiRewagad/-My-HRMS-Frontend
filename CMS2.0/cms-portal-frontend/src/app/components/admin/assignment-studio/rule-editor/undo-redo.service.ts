import { Injectable } from '@angular/core';

export interface UndoableAction {
  rowIndex: number;
  colId: string;
  oldValue: any;
  newValue: any;
}

@Injectable({ providedIn: 'root' })
export class UndoRedoService {
  private undoStack: UndoableAction[] = [];
  private redoStack: UndoableAction[] = [];
  private readonly maxHistory = 100;

  push(action: UndoableAction) {
    this.undoStack.push(action);
    if (this.undoStack.length > this.maxHistory) {
      this.undoStack.shift();
    }
    this.redoStack = [];
  }

  undo(): UndoableAction | null {
    const action = this.undoStack.pop() || null;
    if (action) this.redoStack.push(action);
    return action;
  }

  redo(): UndoableAction | null {
    const action = this.redoStack.pop() || null;
    if (action) this.undoStack.push(action);
    return action;
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  clear() {
    this.undoStack = [];
    this.redoStack = [];
  }
}
