// ICEpdf site: theme toggle, copy buttons, tabs, click-to-load YouTube.
(function () {
  'use strict';

  // Theme: follow the system until the toggle pins one; remember the choice when storage allows.
  var root = document.documentElement;
  try {
    var saved = localStorage.getItem('icepdf-theme');
    if (saved === 'light' || saved === 'dark') root.setAttribute('data-theme', saved);
  } catch (e) { /* storage blocked: follow the system */ }

  function currentTheme() {
    var pinned = root.getAttribute('data-theme');
    if (pinned) return pinned;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('theme-toggle');
    if (toggle) {
      toggle.addEventListener('click', function () {
        var next = currentTheme() === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-theme', next);
        try { localStorage.setItem('icepdf-theme', next); } catch (e) { /* not remembered */ }
      });
    }

    // Copy buttons: data-copy holds a selector for the code to copy.
    document.querySelectorAll('[data-copy]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var source = document.querySelector(btn.getAttribute('data-copy'));
        if (!source || !navigator.clipboard) return;
        navigator.clipboard.writeText(source.textContent).then(function () {
          var label = btn.textContent;
          btn.textContent = 'Copied';
          setTimeout(function () { btn.textContent = label; }, 1500);
        });
      });
    });

    // Tabs, with arrow-key navigation.
    document.querySelectorAll('[data-tabs]').forEach(function (group) {
      var tabs = Array.prototype.slice.call(group.querySelectorAll('[role="tab"]'));
      function select(tab) {
        tabs.forEach(function (t) {
          var on = t === tab;
          t.setAttribute('aria-selected', on ? 'true' : 'false');
          t.tabIndex = on ? 0 : -1;
          var panel = document.getElementById(t.getAttribute('aria-controls'));
          if (panel) panel.hidden = !on;
        });
      }
      tabs.forEach(function (tab, i) {
        tab.addEventListener('click', function () { select(tab); });
        tab.addEventListener('keydown', function (e) {
          var step = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
          if (!step) return;
          var next = tabs[(i + step + tabs.length) % tabs.length];
          select(next);
          next.focus();
        });
      });
    });

    // Videos: show the thumbnail, load the (no-cookie) player only on click.
    document.querySelectorAll('.video[data-youtube]').forEach(function (fig) {
      var id = fig.getAttribute('data-youtube');
      if (!/^[\w-]{11}$/.test(id)) return; // not uploaded yet: keep the placeholder
      var frame = fig.querySelector('.video-frame');
      var title = fig.getAttribute('data-title') || 'ICEpdf demo';
      frame.innerHTML = '';
      var thumb = document.createElement('img');
      thumb.src = 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
      thumb.alt = '';
      thumb.loading = 'lazy';
      var play = document.createElement('button');
      play.className = 'video-play';
      play.type = 'button';
      play.setAttribute('aria-label', 'Play video: ' + title);
      play.innerHTML = '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>';
      play.addEventListener('click', function () {
        var iframe = document.createElement('iframe');
        iframe.src = 'https://www.youtube-nocookie.com/embed/' + id + '?autoplay=1&rel=0';
        iframe.title = title;
        iframe.allow = 'autoplay; encrypted-media; picture-in-picture';
        iframe.allowFullscreen = true;
        frame.innerHTML = '';
        frame.appendChild(iframe);
      });
      frame.appendChild(thumb);
      frame.appendChild(play);
    });
  });
})();
