var ROOT_URL = ".";

function clearReactInput() {
  $('#react_input').val('');
  updateReact();
}

function toggleReactExtraOpts() {
  $('#depict_extra_opts').slideToggle();
}

function smittl(smiles) {
  var i = smiles.indexOf(' ');
  var j = smiles.indexOf('\t');
  if (j >= 0 && (j < i || i < 0))
    i = j;
  if (i < 0)
    return '';
  return smiles.substring(i + 1);
}

function reactUrl(opts, smiles, fmt, w, h) {
  var smi = encodeURIComponent(smiles);
  var smirks = encodeURIComponent(opts.smirks);
  var url = ROOT_URL + '/react/' + opts.style + '/' + fmt + '?smi=' + smi + '&smirks=' + smirks;
  if (w && h)
    url += '&w=' + w + '&h=' + h;
  url += '&abbr=' + opts.abbr;
  url += '&hdisp=' + opts.hdisp;
  if (opts.showtitle)
    url += '&showtitle=' + opts.showtitle;
  if (opts.sma)
    url += '&sma=' + encodeURIComponent(opts.sma);
  if (opts.zoom && opts.zoom !== 130)
    url += '&zoom=' + encodeURIComponent(opts.zoom / 100);
  if (opts.annotate)
    url += '&annotate=' + encodeURIComponent(opts.annotate);
  if (opts.arw)
    url += '&arw=' + encodeURIComponent(opts.arw);
  if (opts.dat !== 'm')
    url += '&dat=' + encodeURIComponent(opts.dat);
  if (opts.flip)
    url += '&f=1';
  if (opts.rotate)
    url += '&r=' + encodeURIComponent(opts.rotate);
  if (opts.reverse)
    url += '&reverse=true';
  return url;
}

function renderReactSMILES(inputs, opts) {
  var result = [];
  var nlines = inputs.length < 500 ? inputs.length : 500;
  if (inputs.length > 500) {
    alert("Only the first 500 entries will be displayed!");
  }

  for (var i = 0; i < nlines; i++) {
    var input = inputs[i].trim();

    // skip empty lines and comments
    if (input.length === 0 || input.charAt(0) === '#')
      continue;

    var title = smittl(input);
    title = title.replace(/^\|[^|]+\|\s+/, "");
    if (!title)
      title = "#" + (1 + i);

    result.push(renderReactResult(opts, input, title));
  }

  return result;
}

function updateReact() {
  var input = $('#react_input').val();
  var result = $('#result');
  result.empty();

  var opts = {
    'smirks':    $('#react_smirks').val().trim(),
    'style':     $("select[name='style'] option:selected").val(),
    'annotate':  $("select[name='annotate'] option:selected").val(),
    'zoom':      $("input[name='zoom']").val(),
    'flip':      $("input[name='flip']").is(':checked'),
    'rotate':    $("input[name='rotate']").val(),
    'sma':       $("input[name='smarts']").val(),
    'hdisp':     $("select[name='hdisp'] option:selected").val(),
    'showtitle': $("input[name='showtitle']").is(':checked'),
    'abbr':      $("select[name='abbr'] option:selected").val(),
    'arw':       $("select[name='arw'] option:selected").val(),
    'dat':       $("select[name='dat'] option:selected").val(),
    'reverse':   $("input[name='reverse']").is(':checked')
  };

  result.removeClass().addClass(opts.style);

  if (!input || !opts.smirks)
    return;

  result.append(renderReactSMILES(input.split("\n"), opts));
}

function renderReactResult(opts, smiles, title) {
  var svgUrl = reactUrl(opts, smiles, 'svg', -1, -1);
  var pngUrl = reactUrl(opts, smiles, 'png', -1, -1);
  var pdfUrl = reactUrl(opts, smiles, 'pdf', -1, -1);

  var $outer = $('<div>').addClass('chemdiv').addClass('scheme');
  var $img = $('<div class="img">').append('<span class="valign-helper"></span>')
    .append($('<a href="' + svgUrl + '">').append($('<img onerror="handleReactImgError(this)">')
      .addClass('chemimg')
      .attr('src', svgUrl)));
  var $div = $('<div class="grid">').append($img);
  var $links = $("<div class='links'>");
  $links.append('<a title="Download SVG" href="' + svgUrl + '" download="' + title + '.svg"><i class="icon-file-svg icon" aria-hidden="true"></i></a><br>');
  $links.append('<a title="Download PNG" href="' + pngUrl + '" download="' + title + '.png"><i class="icon-file-png icon" aria-hidden="true"></i></a><br>');
  $links.append('<a title="Download PDF" href="' + pdfUrl + '" download="' + title + '.pdf"><i class="icon-file-pdf icon" aria-hidden="true"></i></a>');
  $img.append($links);
  if (!opts.showtitle)
    $div.append($('<div class="title">').append(title));
  $outer.append($div);
  return $outer;
}

function extractReactError(reason) {
  if (!reason)
    return 'Unable to render reaction.';

  var tempDom = $('<output>').append($.parseHTML(reason));
  var html = $('div', tempDom).html();
  if (html)
    return html;

  return $('<div>').text(reason).html();
}

function showReactError(img, reason) {
  var message = extractReactError(reason);
  $(img).parent().parent().html($('<div class="error-mesg">').append(message));
  $(img).parent().parent().addClass("error");
}

function handleReactImgError(img) {
  $.ajax({
    url: $(img).attr('src'),
    method: 'GET',
    success: function(data) {
      showReactError(img, data);
    },
    error: function(r) {
      showReactError(img, r.responseText);
    }
  });
}
