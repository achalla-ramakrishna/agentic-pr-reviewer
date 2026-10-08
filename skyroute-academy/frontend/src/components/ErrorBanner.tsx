import { ApiError } from '../api/client';

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.problem.detail : 'Unexpected error';
  const cid = error instanceof ApiError ? error.problem.correlationId : undefined;
  return (
    <div role="alert" className="error">
      {message}
      {cid && <small> (ref {cid})</small>}
    </div>
  );
}
