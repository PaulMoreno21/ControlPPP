import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

@Component({
  selector: 'app-pagination',
  standalone: true,
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss',
})
export class PaginationComponent implements OnChanges {
  @Input() totalItems = 0;
  @Input() pageSize = 15;
  @Input() currentPage = 1;
  @Input() disabled = false;
  @Output() pageChange = new EventEmitter<number>();

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.totalItems / this.pageSize));
  }

  get pages(): (number | '…')[] {
    const count = this.pageCount;
    if (count <= 7) return Array.from({ length: count }, (_, index) => index + 1);

    const candidates = [...new Set([1, 2, this.currentPage - 1, this.currentPage, this.currentPage + 1, count - 1, count])]
      .filter((page) => page >= 1 && page <= count)
      .sort((left, right) => left - right);
    const result: (number | '…')[] = [];
    candidates.forEach((page, index) => {
      if (index > 0 && page - candidates[index - 1] > 1) result.push('…');
      result.push(page);
    });
    return result;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['totalItems'] || changes['pageSize']) && this.currentPage > this.pageCount) {
      this.pageChange.emit(this.pageCount);
    }
  }

  selectPage(page: number): void {
    if (page >= 1 && page <= this.pageCount && page !== this.currentPage) this.pageChange.emit(page);
  }
}
