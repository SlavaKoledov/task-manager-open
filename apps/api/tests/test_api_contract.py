from app.main import app


def test_required_api_routes_exist() -> None:
    registered = {(route.path, method) for route in app.routes for method in getattr(route, "methods", set())}
    expected = {
        ("/lists", "GET"),
        ("/lists", "POST"),
        ("/lists/reorder", "POST"),
        ("/lists/{list_id}", "PATCH"),
        ("/lists/{list_id}", "DELETE"),
        ("/tasks", "GET"),
        ("/tasks/today", "GET"),
        ("/tasks/tomorrow", "GET"),
        ("/tasks/inbox", "GET"),
        ("/lists/{list_id}/tasks", "GET"),
        ("/tasks", "POST"),
        ("/tasks/{task_id}", "PATCH"),
        ("/tasks/{task_id}", "DELETE"),
        ("/tasks/{task_id}/toggle", "POST"),
        ("/tasks/reorder", "POST"),
        ("/tasks/{task_id}/subtasks/reorder", "POST"),
    }

    assert expected.issubset(registered)
