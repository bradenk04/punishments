package main

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type fakeStore struct {
	filter Filter
	types  []string
	fail   bool
}

func (s *fakeStore) List(ctx context.Context, f Filter, kinds []string) ([]Punishment, bool, error) {
	s.filter = f
	s.types = kinds
	if s.fail {
		return nil, false, errors.New("password=SECRET ip=192.0.2.1 webhook=SECRET")
	}
	return []Punishment{{ID: "12345678-1234-1234-1234-123456789abc", PlayerUUID: "12345678-1234-1234-1234-123456789abc", PlayerName: "<script>evil</script>", Type: "BAN", Staff: "staff-secret", Reason: "<script>evil</script>", IssuedAt: 1700000000000}}, true, nil
}
func TestPublicPrivacyAndSearch(t *testing.T) {
	store := &fakeStore{}
	panel := Panel{Store: store, Types: []string{"BAN"}, Branding: "Server"}
	req := httptest.NewRequest("GET", "/api/punishments?search=Player&page=2", nil)
	out := httptest.NewRecorder()
	panel.handler().ServeHTTP(out, req)
	if out.Code != 200 || store.filter.Search != "Player" || store.filter.Page != 2 {
		t.Fatal(out.Code, store.filter)
	}
	if len(store.types) != 1 || store.types[0] != "BAN" {
		t.Fatal(store.types)
	}
	for _, private := range []string{"staff-secret", "192.0.2.1", "password", "webhook", "<script>"} {
		if strings.Contains(out.Body.String(), private) {
			t.Fatal("Private or unescaped field:", private)
		}
	}
	if out.Header().Get("Content-Security-Policy") == "" {
		t.Fatal("Missing CSP")
	}
}
func TestErrorsNeverExposeDatabaseDiagnostics(t *testing.T) {
	panel := Panel{Store: &fakeStore{fail: true}, Types: []string{"BAN"}}
	out := httptest.NewRecorder()
	panel.handler().ServeHTTP(out, httptest.NewRequest("GET", "/api/punishments", nil))
	if out.Code != 503 || strings.Contains(out.Body.String(), "SECRET") {
		t.Fatal(out.Code, out.Body.String())
	}
}
func TestValidationAndReadOnlyRoutes(t *testing.T) {
	panel := Panel{Store: &fakeStore{}, Types: []string{"BAN"}}
	handler := panel.handler()
	for _, test := range []struct {
		method, path string
		status       int
	}{
		{"GET", "/api/punishments?page=-1", 400}, {"GET", "/api/punishments?page=invalid", 400},
		{"GET", "/api/punishments/not-a-uuid", 400}, {"GET", "/api/players/not-a-uuid", 400},
		{"POST", "/api/punishments", 405}, {"DELETE", "/api/punishments/12345678-1234-1234-1234-123456789abc", 405},
		{"GET", "/api/punishments?search=" + strings.Repeat("a", 101), 400},
	} {
		out := httptest.NewRecorder()
		handler.ServeHTTP(out, httptest.NewRequest(test.method, test.path, nil))
		if out.Code != test.status {
			t.Error(test, out.Code)
		}
	}
}
func TestStaffVisibilityIsExplicit(t *testing.T) {
	panel := Panel{Store: &fakeStore{}, Types: []string{"BAN"}, ShowStaff: true}
	out := httptest.NewRecorder()
	panel.handler().ServeHTTP(out, httptest.NewRequest(http.MethodGet, "/api/punishments/12345678-1234-1234-1234-123456789abc", nil))
	if out.Code != 200 || !strings.Contains(out.Body.String(), "staff-secret") {
		t.Fatal(out.Code, out.Body.String())
	}
}
