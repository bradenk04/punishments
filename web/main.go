package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	_ "github.com/go-sql-driver/mysql"
	_ "github.com/jackc/pgx/v5/stdlib"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

type Punishment struct {
	ID         string `json:"id"`
	PlayerUUID string `json:"playerUuid"`
	PlayerName string `json:"playerName,omitempty"`
	Type       string `json:"type"`
	Staff      string `json:"staff,omitempty"`
	IssuedAt   int64  `json:"issuedAt"`
	Reason     string `json:"reason"`
	Expiry     *int64 `json:"expiry"`
	Revoked    bool   `json:"revoked"`
}
type Filter struct {
	Search, Player, ID string
	Page               int
}
type Store interface {
	List(context.Context, Filter, []string) ([]Punishment, bool, error)
}
type SQLStore struct {
	DB     *sql.DB
	Driver string
}
type Panel struct {
	Store     Store
	Types     []string
	ShowStaff bool
	Branding  string
}

const pageSize = 25

func (s SQLStore) quote(name string) string {
	if s.Driver == "mysql" {
		return "`" + name + "`"
	}
	return `"` + name + `"`
}
func (s SQLStore) List(ctx context.Context, filter Filter, types []string) ([]Punishment, bool, error) {
	column := func(name string) string { return "p." + s.quote(name) }
	cast := func(expr string) string {
		if s.Driver == "mysql" {
			return "CAST(" + expr + " AS CHAR)"
		}
		return "CAST(" + expr + " AS TEXT)"
	}
	args := []any{}
	bind := func(value any) string {
		args = append(args, value)
		if s.Driver == "mysql" {
			return "?"
		}
		return fmt.Sprintf("$%d", len(args))
	}
	kinds := []string{}
	for _, kind := range types {
		kinds = append(kinds, bind(kind))
	}
	where := column("type") + " IN (" + strings.Join(kinds, ",") + ")"
	if filter.Player != "" {
		where += " AND " + cast(column("target")) + "=" + bind(filter.Player)
	}
	if filter.ID != "" {
		where += " AND " + cast(column("id")) + "=" + bind(filter.ID)
	}
	if filter.Search != "" {
		escaped := strings.NewReplacer("!", "!!", "%", "!%", "_", "!_").Replace(strings.ToLower(filter.Search))
		where += " AND (LOWER(n." + s.quote("name") + ") LIKE " + bind("%"+escaped+"%") + " ESCAPE '!' OR " + cast(column("target")) + "=" + bind(strings.ToLower(filter.Search)) + " OR " + cast(column("id")) + "=" + bind(strings.ToLower(filter.Search)) + ")"
	}
	query := "SELECT " + strings.Join([]string{cast(column("id")), cast(column("target")), "n." + s.quote("name"), column("type"), cast(column("issuerId")), column("issuedAt"), column("reason"), column("expiry"), column("revoked")}, ",") +
		" FROM punishments p LEFT JOIN punishment_players n ON " + cast(column("target")) + "=" + cast("n."+s.quote("id")) + " WHERE " + where +
		" ORDER BY " + column("issuedAt") + " DESC," + column("id") + " LIMIT " + bind(pageSize+1) + " OFFSET " + bind(filter.Page*pageSize)
	rows, err := s.DB.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()
	result := []Punishment{}
	for rows.Next() {
		var p Punishment
		var name, staff, reason sql.NullString
		var expiry sql.NullInt64
		if err = rows.Scan(&p.ID, &p.PlayerUUID, &name, &p.Type, &staff, &p.IssuedAt, &reason, &expiry, &p.Revoked); err != nil {
			return nil, false, err
		}
		p.PlayerName = name.String
		p.Staff = staff.String
		p.Reason = reason.String
		if expiry.Valid {
			value := expiry.Int64
			p.Expiry = &value
		}
		result = append(result, p)
	}
	if err = rows.Err(); err != nil {
		return nil, false, err
	}
	more := len(result) > pageSize
	if more {
		result = result[:pageSize]
	}
	return result, more, nil
}
func validUUID(value string) bool {
	if len(value) != 36 {
		return false
	}
	for index, ch := range value {
		if index == 8 || index == 13 || index == 18 || index == 23 {
			if ch != '-' {
				return false
			}
		} else if !strings.ContainsRune("0123456789abcdefABCDEF", ch) {
			return false
		}
	}
	return true
}
func (p Panel) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/config", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"branding": p.Branding, "publicTypes": p.Types})
	})
	list := func(w http.ResponseWriter, r *http.Request) {
		filter := Filter{Search: r.URL.Query().Get("search"), Player: r.PathValue("player"), ID: r.PathValue("id")}
		if filter.Search != "" && len(filter.Search) > 100 {
			http.Error(w, "Search is too long", 400)
			return
		}
		if (filter.Player != "" && !validUUID(filter.Player)) || (filter.ID != "" && !validUUID(filter.ID)) {
			http.Error(w, "Invalid UUID", 400)
			return
		}
		filter.Player = strings.ToLower(filter.Player)
		filter.ID = strings.ToLower(filter.ID)
		var err error
		if value := r.URL.Query().Get("page"); value != "" {
			filter.Page, err = strconv.Atoi(value)
		}
		if err != nil || filter.Page < 0 || filter.Page > 10000 {
			http.Error(w, "Invalid page", 400)
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
		defer cancel()
		entries, more, err := p.Store.List(ctx, filter, p.Types)
		if err != nil {
			http.Error(w, "Database temporarily unavailable", 503)
			return
		}
		for index := range entries {
			if !p.ShowStaff {
				entries[index].Staff = ""
			}
		}
		if filter.ID != "" {
			if len(entries) == 0 {
				http.NotFound(w, r)
				return
			}
			writeJSON(w, 200, entries[0])
			return
		}
		if entries == nil {
			entries = []Punishment{}
		}
		writeJSON(w, 200, map[string]any{"items": entries, "page": filter.Page, "hasMore": more})
	}
	mux.HandleFunc("GET /api/punishments", list)
	mux.HandleFunc("GET /api/punishments/{id}", list)
	mux.HandleFunc("GET /api/players/{player}", list)
	mux.HandleFunc("GET /app.js", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/javascript; charset=utf-8")
		fmt.Fprint(w, appJS)
	})
	mux.HandleFunc("GET /style.css", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/css; charset=utf-8")
		fmt.Fprint(w, styleCSS)
	})
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, indexHTML)
	})
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Security-Policy", "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Cache-Control", "no-store")
		mux.ServeHTTP(w, r)
	})
}
func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
func main() {
	driver := os.Getenv("DB_DRIVER")
	if driver == "postgres" {
		driver = "pgx"
	}
	if driver != "mysql" && driver != "pgx" {
		log.Fatal("DB_DRIVER must be mysql or postgres")
	}
	db, err := sql.Open(driver, os.Getenv("DB_DSN"))
	if err != nil {
		log.Fatal("Cannot configure database connection")
	}
	defer db.Close()
	db.SetMaxOpenConns(8)
	db.SetMaxIdleConns(4)
	db.SetConnMaxLifetime(3 * time.Minute)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err = db.PingContext(ctx); err != nil {
		log.Fatal("Cannot connect to database")
	}
	types := []string{}
	for _, value := range strings.Split(env("PUBLIC_TYPES", "BAN"), ",") {
		value = strings.ToUpper(strings.TrimSpace(value))
		switch value {
		case "BAN", "MUTE", "WARN", "KICK":
		default:
			log.Fatal("Invalid PUBLIC_TYPES")
		}
		types = append(types, value)
	}
	panel := Panel{SQLStore{db, driver}, types, os.Getenv("SHOW_STAFF") == "true", env("BRANDING", "Punishments")}
	srv := http.Server{Addr: env("LISTEN_ADDR", "127.0.0.1:8080"), Handler: panel.handler(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 15 * time.Second, IdleTimeout: 30 * time.Second, MaxHeaderBytes: 16384}
	log.Printf("Banlist listening on %s", srv.Addr)
	if err = srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal("HTTP server stopped")
	}
}
func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

const indexHTML = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Punishments</title><link rel="stylesheet" href="/style.css"><script src="/app.js" defer></script></head><body><main><header><p class="eyebrow">PUBLIC RECORDS</p><h1 id="brand">Punishments</h1><p>Search player names, UUIDs or punishment IDs.</p></header><form id="search"><label for="query">Find a record</label><div><input id="query" maxlength="100" placeholder="Player name, UUID or ID"><button>Search</button></div></form><p id="status" role="status"></p><div class="scroll"><table><thead><tr><th>Player</th><th>Type</th><th>Reason</th><th>Issued</th><th>Status</th><th>Reference</th></tr></thead><tbody id="records"></tbody></table></div><nav aria-label="Pages"><button id="prev">Previous</button><span id="page"></span><button id="next">Next</button></nav><section id="detail" hidden><h2>Punishment details</h2><pre id="detail-text"></pre><button id="close">Close details</button></section></main></body></html>`
const appJS = `let page=0,search='',player='';const byId=id=>document.getElementById(id);async function load(){byId('status').textContent='Loading…';try{const path=player?'/api/players/'+encodeURIComponent(player):'/api/punishments';const response=await fetch(path+'?page='+page+'&search='+encodeURIComponent(search));if(!response.ok)throw Error('Records are temporarily unavailable.');const data=await response.json();byId('records').replaceChildren();for(const item of data.items){const row=document.createElement('tr');const values=[item.playerName||item.playerUuid,item.type,item.reason||'No reason',new Date(item.issuedAt).toLocaleString(),item.type==='KICK'?'Recorded':item.revoked?'Revoked':item.expiry&&item.expiry<=Date.now()?'Expired':'Active',item.id];values.forEach((value,index)=>{const cell=document.createElement('td');if(index===0||index===5){const button=document.createElement('button');button.className='link';button.textContent=value;button.onclick=()=>index===0?(player=item.playerUuid,page=0,load()):details(item.id);cell.append(button);}else cell.textContent=value;row.append(cell);});byId('records').append(row);}byId('page').textContent='Page '+(page+1);byId('prev').disabled=page===0;byId('next').disabled=!data.hasMore;byId('status').textContent=data.items.length?'':'No records found.';}catch(error){byId('status').textContent=error.message;}}async function details(id){const response=await fetch('/api/punishments/'+encodeURIComponent(id));if(!response.ok){byId('status').textContent='Record unavailable';return;}const item=await response.json();byId('detail-text').textContent=['ID: '+item.id,'Player: '+(item.playerName||item.playerUuid),'UUID: '+item.playerUuid,'Type: '+item.type,'Reason: '+(item.reason||'No reason'),'Issued: '+new Date(item.issuedAt).toLocaleString(),'Expires: '+(item.expiry?new Date(item.expiry).toLocaleString():'Permanent'),'Status: '+(item.type==='KICK'?'Recorded':item.revoked?'Revoked':item.expiry&&item.expiry<=Date.now()?'Expired':'Active'),...(item.staff?['Issuer: '+item.staff]:[])].join('\n');byId('detail').hidden=false;}byId('search').onsubmit=event=>{event.preventDefault();search=byId('query').value;player='';page=0;load();};byId('prev').onclick=()=>{page--;load();};byId('next').onclick=()=>{page++;load();};byId('close').onclick=()=>byId('detail').hidden=true;fetch('/api/config').then(r=>r.json()).then(c=>{byId('brand').textContent=c.branding;document.title=c.branding;});load();`
const styleCSS = `:root{font-family:system-ui,sans-serif;color:#e8eef5;background:#101821;color-scheme:dark}body{margin:0}main{max-width:1100px;margin:60px auto;padding:24px}header{margin-bottom:36px}h1{font-size:42px;margin:8px 0}.eyebrow{color:#6ee7bd;font-size:12px;letter-spacing:2px}label{display:block;margin-bottom:10px}form div{display:flex;gap:12px}input{flex:1;min-width:0;padding:12px;background:#172330;border:1px solid #475569;border-radius:6px}button{padding:10px 16px;border-radius:6px;border:1px solid #475569;background:#243446;color:inherit;cursor:pointer}button:disabled{opacity:.4;cursor:default}.scroll{overflow:auto}table{width:100%;border-collapse:collapse;margin:24px 0}th,td{text-align:left;padding:14px 10px;border-bottom:1px solid #334155;vertical-align:top}th{color:#9cacc1;font-size:12px;text-transform:uppercase}td:last-child{font-size:12px;word-break:break-all}.link{border:0;background:transparent;padding:0;text-align:left;color:#6ee7bd}nav{display:flex;align-items:center;justify-content:space-between}#detail{margin-top:30px;padding:20px;background:#172330}pre{white-space:pre-wrap;overflow-wrap:anywhere}@media(max-width:600px){main{margin:20px auto}h1{font-size:32px}}`
