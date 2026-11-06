import type { Page } from 'playwright';

export interface SelectorCandidate {
  selector: string;
  type: 'CSS' | 'XPATH';
  matchCount: number;
  sample: string;
}

export interface PickResult {
  itemSelector: string;
  itemSelectorType: 'CSS';
  itemMatchCount: number;
  itemPreview: string;
  fieldSelectors: SelectorCandidate[];
  element: {
    tag: string;
    text: string;
    html: string;
    preview: string;
  };
}

export async function pickAt(page: Page, x: number, y: number): Promise<PickResult> {
  return page.evaluate(
    ({ pointX, pointY }) => {
      const element = document.elementFromPoint(pointX, pointY);
      if (!element) {
        throw new Error('No element at that point');
      }

      const LEAF_TAGS = new Set([
        'a', 'span', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'img', 'button', 'strong', 'em', 'b', 'i', 'small', 'td', 'th', 'label',
      ]);

      const stableClasses = (node: Element): string[] =>
        Array.from(node.classList)
          .filter((name) => name.length > 0 && name.length < 40 && !/^(_|js-|is-)/.test(name))
          .slice(0, 2);

      const signature = (node: Element): string => {
        const tag = node.tagName.toLowerCase();
        const classes = stableClasses(node);
        return tag + classes.map((name) => '.' + CSS.escape(name)).join('');
      };

      const relativePath = (node: Element, root: Element): string => {
        const parts: string[] = [];
        let cursor: Element | null = node;
        while (cursor && cursor !== root) {
          const base = signature(cursor);
          const parent: Element | null = cursor.parentElement;
          let part = base;
          if (parent) {
            const sameSignature = Array.from(parent.children).filter(
              (child: Element) => child.tagName === cursor!.tagName && signature(child) === base
            );
            if (sameSignature.length > 1) {
              const sameTag = Array.from(parent.children).filter(
                (child: Element) => child.tagName === cursor!.tagName
              );
              part += `:nth-of-type(${sameTag.indexOf(cursor) + 1})`;
            }
          }
          parts.unshift(part);
          cursor = cursor.parentElement;
        }
        return parts.join(' > ');
      };

      const absoluteXPath = (node: Element): string => {
        const parts: string[] = [];
        let cursor: Element | null = node;
        while (cursor) {
          let index = 1;
          let sibling = cursor.previousElementSibling;
          while (sibling) {
            if (sibling.tagName === cursor.tagName) {
              index++;
            }
            sibling = sibling.previousElementSibling;
          }
          parts.unshift(`${cursor.tagName.toLowerCase()}[${index}]`);
          cursor = cursor.parentElement;
        }
        return '/' + parts.join('/');
      };

      const itemRoot = (() => {
        let cursor: Element | null = element;
        for (let depth = 0; depth < 7 && cursor; depth++) {
          const parent: Element | null = cursor.parentElement;
          if (!parent) {
            break;
          }
          const sig = signature(cursor);
          const siblings = Array.from(parent.children).filter((child: Element) =>
            child.matches(sig)
          );
          const total = document.querySelectorAll(sig).length;
          if (
            siblings.length >= 2 &&
            total >= 2 &&
            total <= 400 &&
            !LEAF_TAGS.has(cursor.tagName.toLowerCase())
          ) {
            return { node: cursor, selector: sig, count: total };
          }
          cursor = parent;
        }

        const fallback =
          element.closest('article, li, tr, .quote, .item, .product, .card') ??
          element.parentElement ??
          element;
        return {
          node: fallback,
          selector: signature(fallback),
          count: document.querySelectorAll(signature(fallback)).length,
        };
      })();

      const resolved = (() => {
        const detected = itemRoot;
        let node = detected.node;
        let selector = detected.selector;
        let count = detected.count;

        for (let depth = 0; depth < 3; depth++) {
          const children = Array.from(node.children);
          if (children.length !== 1) {
            break;
          }
          const child = children[0];
          const tag = child.tagName.toLowerCase();
          const childSelector = signature(child);
          const childCount = document.querySelectorAll(childSelector).length;
          if (
            ['article', 'section', 'div', 'li', 'tr'].includes(tag) &&
            !LEAF_TAGS.has(tag) &&
            childCount >= 2 &&
            childCount <= 400
          ) {
            node = child;
            selector = childSelector;
            count = childCount;
            continue;
          }
          break;
        }

        return { node, selector, count };
      })();

      const candidates: SelectorCandidate[] = [];
      const addCandidate = (selector: string, type: 'CSS' | 'XPATH') => {
        if (!selector || candidates.some((item) => item.selector === selector && item.type === type)) {
          return;
        }
        try {
          let matches: Element[];
          if (type === 'CSS') {
            matches = Array.from(resolved.node.querySelectorAll(selector));
          } else {
            const result = document.evaluate(
              selector,
              resolved.node,
              null,
              XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
              null
            );
            matches = [];
            for (let index = 0; index < result.snapshotLength; index++) {
              const node = result.snapshotItem(index);
              if (node instanceof Element) {
                matches.push(node);
              }
            }
          }
          if (matches.length === 0) {
            return;
          }
          const sample = (matches[0].textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 120);
          candidates.push({ selector, type, matchCount: matches.length, sample });
        } catch {
          // Ignore invalid selector variants.
        }
      };

      addCandidate(relativePath(element, resolved.node), 'CSS');
      addCandidate(signature(element), 'CSS');

      const classes = stableClasses(element);
      if (classes.length > 1) {
        addCandidate(element.tagName.toLowerCase() + '.' + CSS.escape(classes[0]), 'CSS');
      }
      if (element.tagName.toLowerCase() === 'a' && element.hasAttribute('href')) {
        addCandidate('a[href]', 'CSS');
      }
      if (element.tagName.toLowerCase() === 'img' && element.hasAttribute('src')) {
        addCandidate('img[src]', 'CSS');
      }
      addCandidate(absoluteXPath(element), 'XPATH');

      const text = ((element as HTMLElement).innerText ?? element.textContent ?? '')
        .replace(/\s+/g, ' ')
        .trim();

      return {
        itemSelector: resolved.selector,
        itemSelectorType: 'CSS' as const,
        itemMatchCount: resolved.count,
        itemPreview: resolved.node.outerHTML.replace(/\s+/g, ' ').trim().slice(0, 600),
        fieldSelectors: candidates.slice(0, 5),
        element: {
          tag: element.tagName.toLowerCase(),
          text: text.slice(0, 500),
          html: element.outerHTML.replace(/\s+/g, ' ').trim().slice(0, 1500),
          preview: text.slice(0, 300),
        },
      };
    },
    { pointX: x, pointY: y }
  );
}
