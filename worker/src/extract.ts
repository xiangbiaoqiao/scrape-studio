import type { Page } from 'playwright';

export interface PreviewField {
  name: string;
  selector: string;
  selectorType?: string;
  attribute?: string;
  multiple?: boolean;
}

export interface PreviewResult {
  rows: Array<Record<string, unknown>>;
  count: number;
  pageTitle: string;
  blocked: boolean;
  blockReason: string | null;
  warnings: string[];
}

export async function extract(
  page: Page,
  itemSelector: string | null,
  itemSelectorType: string | null,
  fields: PreviewField[],
  limit: number
): Promise<{
  rows: Array<Record<string, unknown>>;
  count: number;
  totalMatches: number;
}> {
  return page.evaluate(
    ({ selector, selectorType, fieldSpecs, maxRows }) => {
      const normalizeType = (value: string | null | undefined): 'CSS' | 'XPATH' =>
        (value ?? 'CSS').toUpperCase() === 'XPATH' ? 'XPATH' : 'CSS';

      const queryAll = (root: ParentNode, rawSelector: string, type: string): Element[] => {
        const kind = normalizeType(type);
        const relative = (() => {
          if (kind !== 'XPATH' || !(root instanceof Element)) {
            return rawSelector;
          }
          const trimmed = rawSelector.trim();
          if (trimmed.startsWith('//')) {
            return '.' + trimmed;
          }
          if (trimmed.startsWith('/')) {
            return '.' + trimmed;
          }
          return trimmed;
        })();

        if (kind === 'CSS') {
          return Array.from(root.querySelectorAll(relative));
        }

        const result = document.evaluate(
          relative,
          root,
          null,
          XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
          null
        );
        const nodes: Element[] = [];
        for (let index = 0; index < result.snapshotLength; index++) {
          const node = result.snapshotItem(index);
          if (node instanceof Element) {
            nodes.push(node);
          }
        }
        return nodes;
      };

      const readValue = (element: Element, attribute: string | undefined): string => {
        const name = (attribute ?? 'text').toLowerCase();
        if (name === 'text' || name === '') {
          return ((element as HTMLElement).innerText ?? element.textContent ?? '').trim();
        }
        if (name === 'html') {
          return element.innerHTML;
        }
        return element.getAttribute(attribute ?? name) ?? '';
      };

      const roots = selector && selector.trim()
        ? queryAll(document, selector.trim(), selectorType ?? 'CSS')
        : [document.documentElement];

      const rows: Array<Record<string, unknown>> = [];
      for (const root of roots.slice(0, maxRows)) {
        const row: Record<string, unknown> = {};
        for (const field of fieldSpecs) {
          if (!field.selector || !field.selector.trim()) {
            row[field.name] = field.multiple ? [] : '';
            continue;
          }
          const matches = queryAll(root, field.selector.trim(), field.selectorType ?? 'CSS');
          if (field.multiple) {
            row[field.name] = matches.map((element) => readValue(element, field.attribute));
          } else {
            row[field.name] = matches.length > 0 ? readValue(matches[0], field.attribute) : '';
          }
        }
        rows.push(row);
      }

      return {
        rows,
        count: rows.length,
        totalMatches: roots.length,
      };
    },
    {
      selector: itemSelector,
      selectorType: itemSelectorType,
      fieldSpecs: fields,
      maxRows: limit,
    }
  );
}
