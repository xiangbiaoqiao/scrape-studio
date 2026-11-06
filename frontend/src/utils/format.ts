export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(value?: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function timeAgo(value?: string | null): string {
  if (!value) {
    return 'never';
  }
  const date = new Date(value).getTime();
  if (Number.isNaN(date)) {
    return 'never';
  }
  const seconds = Math.round((Date.now() - date) / 1000);
  if (seconds < 60) {
    return 'just now';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}h ago`;
  }
  const days = Math.floor(hours / 24);
  if (days < 30) {
    return `${days}d ago`;
  }
  return formatDate(value);
}

export function formatDuration(milliseconds?: number | null): string {
  if (milliseconds === null || milliseconds === undefined) {
    return '—';
  }
  if (milliseconds < 1000) {
    return `${milliseconds} ms`;
  }
  const seconds = milliseconds / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${minutes}m ${rest}s`;
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat().format(value);
}

export function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

export function statusTagType(
  status?: string | null
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'BLOCKED':
      return 'warning';
    case 'RUNNING':
      return 'primary';
    default:
      return 'info';
  }
}

export function statusLabel(status?: string | null): string {
  if (!status) {
    return 'Not run yet';
  }
  return status.charAt(0) + status.slice(1).toLowerCase();
}

export function triggerLabel(trigger?: string | null): string {
  return trigger === 'SCHEDULE' ? 'Scheduled' : 'Manual';
}
