"use strict";
const $ = (id) => document.getElementById(id);
let page = 0;
let selectedCode = null;
let latestUrl = "";
let refreshVersion = 0;
function notice(id, text, error = false) { $(id).textContent = text; $(id).classList.toggle("error", error); }
async function api(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || data.detail || `Request failed (${response.status}).`);
  }
  return response.status === 204 ? null : response.json();
}
function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}
function button(text, action) {
  const element = node("button", text);
  element.type = "button";
  element.addEventListener("click", action);
  return element;
}
async function copy(value) {
  try { await navigator.clipboard.writeText(value); notice("library-notice", "Link copied."); }
  catch { notice("library-notice", "Copy was unavailable. Select and copy the short URL manually.", true); }
}
async function refresh() {
  const version = ++refreshVersion;
  $("refresh").disabled = true;
  try {
    const rows = await api(`/api/links?limit=20&offset=${page * 20}`);
    if (version !== refreshVersion) return;
    $("links").replaceChildren();
    for (const link of rows) {
      const row = node("tr"); const main = node("td");
      const anchor = node("a", `/s/${link.code}`, "link-name");
      anchor.href = link.shortUrl; anchor.target = "_blank"; anchor.rel = "noopener noreferrer";
      main.append(anchor, node("p", link.url, "destination"));
      const state = node("td"); state.append(node("span", link.status, `badge ${link.status}`));
      const actions = node("div", undefined, "actions");
      actions.append(button("Copy", () => copy(link.shortUrl)), button("Insights", () => insights(link.code)));
      if (link.status === "active") actions.append(button("Disable", async () => {
        if (!confirm(`Disable /s/${link.code}? The link will stop redirecting.`)) return;
        try { await api(`/api/links/${link.code}`, {method:"DELETE"}); notice("library-notice", "Link disabled."); await refresh(); if (selectedCode === link.code) await insights(link.code); }
        catch (error) { notice("library-notice", error.message, true); }
      }));
      const actionCell = node("td"); actionCell.append(actions);
      row.append(main, state, node("td", String(link.visits)), actionCell); $("links").append(row);
    }
    $("empty").hidden = rows.length !== 0; $("metric-links").textContent = rows.length;
    $("metric-active").textContent = rows.filter(row => row.status === "active").length;
    $("metric-visits").textContent = rows.reduce((sum,row) => sum + row.visits,0);
    $("previous").disabled = page === 0; $("next").disabled = rows.length < 20;
    $("page-label").textContent = `Page ${page + 1}`;
  } catch (error) { notice("library-notice", error.message, true); }
  finally { if (version === refreshVersion) $("refresh").disabled = false; }
}
async function insights(code) {
  selectedCode = code;
  try {
    const [link, stats] = await Promise.all([api(`/api/links/${code}`), api(`/api/links/${code}/stats`)]);
    if (selectedCode !== code) return;
    $("details").hidden = false; $("details-title").textContent = `Insights · /s/${code}`;
    $("detail-destination").textContent = link.url;
    $("detail-meta").replaceChildren(node("span", `${link.visits} total visits`), node("span", `Status: ${link.status}`), node("span", `Expires: ${link.expiresAt ? new Date(link.expiresAt).toLocaleString() : "Never"}`));
    $("daily-bars").replaceChildren();
    const max = Math.max(1, ...stats.days.map(day => day.visits));
    for (const day of stats.days) {
      const bar = node("div", undefined, "bar"); bar.style.height = `${Math.max(2, day.visits / max * 125)}px`;
      bar.title = `${day.date}: ${day.visits} visits`; bar.setAttribute("role", "img"); bar.setAttribute("aria-label", bar.title);
      $("daily-bars").append(bar);
    }
    $("details").scrollIntoView({behavior:"smooth",block:"nearest"});
  } catch (error) { notice("library-notice", error.message, true); }
}
$("create-form").addEventListener("submit", async (event) => {
  event.preventDefault(); $("create-button").disabled = true; notice("create-notice", "Creating link…");
  const payload = {url:$("url").value, alias:$("alias").value || null, expiresInMinutes:$("expiry").value ? Number($("expiry").value) : null};
  try {
    const link = await api("/api/links", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)});
    latestUrl = link.shortUrl; $("created-url").textContent = latestUrl; $("created-url").href = latestUrl;
    $("created").hidden = false; notice("create-notice", "Your short link is ready."); page = 0; await refresh();
  } catch (error) { notice("create-notice", error.message, true); }
  finally { $("create-button").disabled = false; }
});
$("copy-created").addEventListener("click", () => copy(latestUrl));
$("refresh").addEventListener("click", () => {notice("library-notice", ""); refresh(); if(selectedCode) insights(selectedCode);});
$("previous").addEventListener("click", () => {if(page > 0) page--; refresh();});
$("next").addEventListener("click", () => {page++; refresh();});
$("close-details").addEventListener("click", () => {selectedCode = null; $("details").hidden = true;});
api("/actuator/health").then(data => {$("health").textContent = data.status === "UP" ? "Service online" : "Service unavailable"; $("health").classList.toggle("up", data.status === "UP");}).catch(() => {$("health").textContent = "Service unavailable";});
refresh();
