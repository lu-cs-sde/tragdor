

async function loadData() {
  const customRep = location.search.split(/[&?]/).find(x => x.startsWith(`report=`))
  let reportsFileStr = customRep ? customRep.slice('report='.length) : 'reports.json';
  if (reportsFileStr == 'ask') {
    reportsFileStr = prompt('Enter reports file path');
  }
  const reportsFiles = reportsFileStr.split(',');

  let parts = [];

  // Intentionally fetch in sequence to avoid fetching a bajillion things simultaneously
  for (let i = 0; i < reportsFiles.length; ++i) {
    const reportsFile = reportsFiles[i];
    const response = await fetch(`${reportsFile}?cacheBuster=${Date.now()}`);
    if (!response.ok) {
      throw new Error(`Error fetching '${reportsFile}': ${response.status}`);
    }
    const json = await response.json();
    if (Array.isArray(json)) {
      return { reportsFile, config: null, reports: json, uptimeMs: 0 };
    }
    if (!json.config || !json.reports) {
      throw new Error(`Unexpected structure of response`);
    }
    parts.push({
      reportsFile,
      config: json.config,
      reports: json.reports,
      uptimeMs: json.uptimeMs ?? 0,
    });
  }
  // const parts = await Promise.all(reportsFiles.map(async reportsFile => {
  // }));

  if (parts.length === 1) {
    // Common case, single file
    return parts[0];
  }

  // Multiple files, need to merge


  return {
    config: Object.assign({}, ...parts.map(rep => ({ [rep.reportsFile]: rep.config }))),
    reports: parts.map(rep => rep.reports.map(inner => ({ ...inner, cfgSource: rep.reportsFile }))).flat(),
    uptimeMs: parts.reduce((a, b) => a + (b.uptimeMs??0), 0),
  };
}

const argToStr = (arg) => {
  switch (arg.type) {
    case 'string':
      return `"${arg.value}"`;
    case 'integer':
    case 'bool':
      return arg.value;
    case 'collection':
      return `[${arg.value.entries.map(argToStr).join(', ')}]`;
    case 'nodeLocator':
      return arg.value.value ? `${arg.value.value.result.type.split('.').slice(-1)[0]} at ${stepsToStr(arg.value.value.steps)}` : 'null';
    default:
      return JSON.stringify(arg);
  }
}

const stepToStr = (step) => {
  switch (step.type) {
    case 'child':
      return `${step.value}`;
    case 'nta':
      return `${step.value.property.name}(${step.value.property.args.map(argToStr).join(', ')})`;
    default:
      // TODO improve for nta/tal
      return JSON.stringify(step.value);
  }
}
const stepsToStr = steps => `[${steps.map(stepToStr).join(', ')}]`;

const createMessage = (message, title = 'Message:') => {
  const container = document.createElement('p');
  container.classList.add('subject');
  const header = document.createElement('span');
  header.classList.add('details-header');
  header.innerText = title;
  container.appendChild(header);

  const content = document.createElement('span');
  content.classList.add('details-content');
  content.classList.add('details-content-message');
  content.innerText = `${message}`;
  container.appendChild(content);
  return container;
}
const createStackTrace = (elements) => {
  const container = document.createElement('p');
  container.classList.add('subject');
  const header = document.createElement('div');
  header.classList.add('details-header');
  header.innerText = 'Stack Trace';
  container.appendChild(header);

  const content = document.createElement('div');
  content.classList.add('details-content');
  content.classList.add('details-content-stacktrace');
  elements.forEach(({ line, "class": clazz, mth, file }) => {
    const row = document.createElement('div');

    const addPart = (text, cls='') => {
      const part = document.createElement('span');
      if (cls) { part.classList.add(cls); }
      part.innerText = text;
      row.appendChild(part);
    };
    addPart(`${clazz}`, 'syntax-type');
    addPart('.');
    addPart(`${mth}`, 'syntax-attr');
    addPart(`(`);
    addPart(`${file}`, 'syntax-string');
    addPart(`:`);
    addPart(`${line}`, 'syntax-int');
    addPart(`)`);
    // row.innerText = `${clazz}.${mth}: ${line}`;
    content.appendChild(row);
  });
  container.appendChild(content);
  return container;

}
const createSubject = (subjectData, title = 'Node:') => {
  const container = document.createElement('p');
  container.classList.add('subject');
  const header = document.createElement('span');
  header.classList.add('details-header');
  header.innerText = title;
  container.appendChild(header);

  const content = document.createElement('span');
  content.classList.add('details-content');
  content.classList.add('details-content-subject');
  content.innerText = `${subjectData.result.type.split('.').slice(-1)[0]} at ${stepsToStr(subjectData.steps)}`;
  container.appendChild(content);
  return container;
}

const createProp = (propData, title='Prop:') => {
  const container = document.createElement('p');
  container.classList.add('prop');
  const header = document.createElement('span');
  header.classList.add('details-header');
  header.innerText = title;
  container.appendChild(header);
  const content = document.createElement('span');
  content.classList.add('details-content');
  content.classList.add('details-content-prop');
  content.innerText = `${propData.name}${propData.args?.length ? `(${propData.args.map(argToStr).join(', ')})` : ''}`;
  container.appendChild(content);
  return container;
}

const createRpcLine = (/** @type {({ type: 'plain'; value: string; } | { type: 'stdout'; value: string; } | { type: 'stderr'; value: string; } | { type: 'streamArg'; value: string; } | { type: 'arr'; value: RpcBodyLine[]; } | { type: 'node'; value: NodeLocator; } | { type: 'dotGraph'; value: string; } | { type: 'highlightMsg'; value: HighlightableMessage; } | { type: 'tracing'; value: Tracing; } | { type: 'html'; value: string; })} */ line) => {
  if (!line) {
    console.error('createRpcLine called without line??', new Error('dump'));
  }
  switch (line.type) {
    case 'plain':
    case 'stdout':
    case 'stderr': {
      const plain = document.createElement('span');
      plain.innerText = line.value;
      return plain;
    }

    case 'arr': {
      const holder = document.createElement('div');
      holder.classList.add('rpc-arr-holder');
      const addArrBracket = char => {
        const div = document.createElement('div');
        div.innerText = char;
        holder.appendChild(div);
      }
      addArrBracket('[')

      const isInlineIsh = (entry) => {
        switch (entry.type) {
          case 'plain':
          case 'stdout':
          case 'stderr':
            return entry.value.trim() === entry.value;
          default:
            return false;
        }
      }
      for (let i = 0; i < line.value.length; ++i) {
        holder.appendChild(createRpcLine(line.value[i]));
        if (isInlineIsh(line.value[i]) && i < line.value.length - 1 && isInlineIsh(line.value[i + 1])) {
          // Two values without linebreaks between them, force a linebreak
          holder.appendChild(document.createElement('br'));
        }
      }
      // line.value.forEach(inner => {
        // holder.appendChild(document.createTextNode(JSON.stringify(inner)));
      // });
      addArrBracket(']')
      return holder;
    }

    case 'node': {
      const holder = document.createElement('div');
      holder.style.display = 'inline'
      holder.innerText = `${line.value.result.type.split('.').slice(-1)[0]} at [${line.value.steps.map(stepToStr).join(', ')}]`;
      return holder;
    }

    case 'highlightMsg': {
      const holder = document.createElement('div');
      const loc = document.createElement('span');
      loc.classList.add('rpc-highlight-loc')
      loc.innerText = `[${line.value.start >>> 12}:${line.value.start & 0xFFF}→${line.value.end >>> 12}:${line.value.end & 0xFFF}]`;
      holder.appendChild(loc);
      const str = document.createElement('pre');
      str.classList.add('rpc-highlight-str');
      str.innerText = line.value.msg;
      holder.appendChild(str);
      return holder;
    }

    default: {
      const raw = document.createElement('pre');
      raw.innerText = JSON.stringify(line);
      return raw;
    }

  }
}
const createValue = (value, title) => {
  const container = document.createElement('p');
  container.classList.add('value');
  const header = document.createElement('span');
  header.classList.add('details-header');
  header.innerText = title;
  container.appendChild(header);

  const content = document.createElement('span');
  content.appendChild(createRpcLine(value));
  container.appendChild(content);
  return container;
};

const createIntermediates = (steps, headerLbl = `Intermediate props (#${steps.length}):`) => {
  const container = document.createElement('p');
  container.classList.add('intermediates')
  const header = document.createElement('span');
  header.classList.add('details-header');
  header.innerText = headerLbl;
  container.appendChild(header);

  const content = document.createElement('div');
  content.style.padding = '0.25rem';
  steps.forEach(locProp => {
    const inner = document.createElement('div');
    inner.classList.add('intermediate-step');
    inner.appendChild(createSubject(locProp.loc));
    inner.appendChild(createProp(locProp.prop));
    content.appendChild(inner);
  });
  container.appendChild(content);
  // if (steps.length)
  return container;
}

const createCprButton = (locator, property) => {
  const fixPropertyName = (prop) => ({
    ...prop,
    name: prop.name.split('(')[0],
  });

  const cprButton = document.createElement('a');
  cprButton.target = '_blank';
  cprButton.innerText = 'Open in CodeProber';
  cprButton.href = `http://localhost:8000?editor=Monaco&settings=${encodeURIComponent(JSON.stringify({
    editorContents: '',
    astCacheStrategy: 'NONE',
    probeWindowStates: [{
      modalPos: { x: 100, y: 100 },
      data: {
        type: 'probe',
        locator,
        property: fixPropertyName(property),
        nested: {},
      },
    }],
  }))}`
  return cprButton;
}

const createRawSummary = (entry) => {
  const rawSum = document.createElement('summary');
  rawSum.innerText = `Raw data`;
  const detailsContent = document.createElement('pre');
  detailsContent.innerText = JSON.stringify(entry.details, null, 2);
  const rawDets = document.createElement('details');
  rawDets.appendChild(rawSum);
  rawDets.appendChild(detailsContent);
  return rawDets;
}
function createRow(entry, config) {
  const row = document.createElement('div');
  row.classList.add('row');

  const rowWrapper = document.createElement('div');
  rowWrapper.classList.add('rowWrapper');

  const title = document.createElement('h1');
  title.classList.add('title');
  title.innerText = entry.type;

  const message = document.createElement('p');
  message.classList.add('message');
  message.innerText = entry.message;

  const details = document.createElement('details');
  const summary = document.createElement('summary');
  summary.innerText = 'Details';
  details.appendChild(summary);
  if (entry.cfgSource !== undefined) {
      details.appendChild(createMessage(entry.cfgSource, 'From:'));
  }
  if (entry.toolIdx !== undefined) {
    let startingPoint = config;
    if (startingPoint && entry.cfgSource) {
      startingPoint = startingPoint[entry.cfgSource];
    }
    const cfgArgs = startingPoint?.tragdor?.tool?.args;
    if (cfgArgs?.length) {
      if (cfgArgs.some(x => Array.isArray(x))) {
        const ent = cfgArgs[entry.toolIdx];
        if (ent) {
          // const toolInfo = document.createElement('div');
          // toolInfo.innerText = `Args: ${JSON.stringify(ent)}`;
          details.appendChild(createMessage(JSON.stringify(ent), 'Args:'));

        }
      }
    }
  }
  switch (entry.type) {
    case 'PROPERTY_VALUE_DIFF': {
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.property));

      // details.appendChild(createCprButton(entry.details.subject, entry.details.property));
      details.appendChild(createValue(entry.details.freshAstValue, 'Fresh AST value:'));
      details.appendChild(createValue(entry.details.valueAfterIntermediates, 'Cached AST value:'));
      details.appendChild(createIntermediates(entry.details.intermediateSteps));
      details.appendChild(createRawSummary(entry));
      break;
    }
    case 'NON_IDEMPOTENT_PROPERTY_EQUATION': // Fall-through
    case 'NON_IDEMPOTENT_PROPERTY_EQUATION_AFTER_RESET': {
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.property));

      // details.appendChild(createCprButton(entry.details.subject, entry.details.property));
      details.appendChild(createValue(entry.details.referenceRunValue, 'Reference run value:'));
      details.appendChild(createValue(entry.details.repeatInvocationValue, 'Repeat invocation value:'));
      details.appendChild(createValue(entry.details.freshAstValue, 'Fresh AST value:'));
      if (entry.details.intermediateSteps) {
        details.appendChild(createIntermediates(entry.details.intermediateSteps));
      }
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'EXCEPTION_THROWN': {
      details.appendChild(createMessage(entry.details.message, 'Message:'));
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.prop));
      // details.appendChild(createCprButton(entry.details.subject, entry.details.prop));
      details.appendChild(createStackTrace(entry.details.stackTrace));
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'PROPERTY_VALUE_DIFF_IN_REFERENCE_RUN': {
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.property));

      // details.appendChild(createCprButton(entry.details.subject, entry.details.property));
      details.appendChild(createValue(entry.details.referenceRunValue, 'Reference run value:'));
      if (entry.details.referenceRunValue) {
        details.appendChild(createValue(entry.details.randomSearchValue, 'Random search value:'));
      }
      details.appendChild(createValue(entry.details.freshAstValue, 'Fresh AST value:'));
      if (entry.details.intermediateSteps) {
        details.appendChild(createIntermediates(entry.details.intermediateSteps, `Fresh perturbation steps (#${entry.details.intermediateSteps.length})`));
      }
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'FLAKY_PROPERTY':
    case 'FLAKY_PROPERTY_IN_REFERENCE_RUN': {
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.locator ?? entry.details.prop));
      // details.appendChild(createCprButton(entry.details.subject, entry.details.locator ?? entry.details.prop));
      if (entry.details.exampleValue1 && entry.details.exampleValue2) {
        details.appendChild(createValue(entry.details.exampleValue1, 'Example value 1:'));
        details.appendChild(createValue(entry.details.exampleValue2, 'Example value 2:'));
      }
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'MUTATED_INTRINSICS': {
      details.appendChild(createSubject(entry.details.subject));

      const container = document.createElement('p');
      container.classList.add('intrinsic');
      const header = document.createElement('span');
      header.classList.add('details-header');
      header.innerText = 'Intrinsic:';
      container.appendChild(header);
      const content = document.createElement('span');
      content.classList.add('details-content');
      content.innerText = `${entry.details.intrinsic}`;
      container.appendChild(content);
      details.appendChild(container);

      if (entry.details.value1 && entry.details.value2) {
        details.appendChild(createValue(entry.details.value1, 'Example value 1:'));
        details.appendChild(createValue(entry.details.value2, 'Example value 2:'));
      }
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'UNATTACHED_NODE': {
      details.appendChild(createSubject(entry.details.subject));
      details.appendChild(createProp(entry.details.property));
      // details.appendChild(createCprButton(entry.details.subject, entry.details.property));
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'FAILED_FINDING_NODE_IN_NEW_AST': {
      details.appendChild(createSubject(entry.details.subject));
      // details.appendChild(createCprButton(entry.details.subject, entry.details.property));
      details.appendChild(createRawSummary(entry));
      break;
    }

    case 'NODE_REACHABLE_THROUGH_TWO_PATHS': {
      const create = (titleText, steps) => {
        const holder = document.createElement('div');
        const title = document.createElement('span');
        const header = document.createElement('span');
        header.classList.add('details-header');
        header.innerText = titleText;
        holder.appendChild(header);

        const content = document.createElement('span');
        content.classList.add('details-content');
        content.innerText = `[${steps.map(stepToStr).join(', ')}]`;
        holder.appendChild(content);

        details.appendChild(holder);
      }
      create('Path 1:', entry.details.firstPath);
      create('Path 2:', entry.details.secondPath);
      details.appendChild(createRawSummary(entry));
      break;
    }

    default: {
      // Default to rendering the whole data
      const detailsContent = document.createElement('pre');
      detailsContent.innerText = JSON.stringify(entry.details, null, 2);
      details.appendChild(detailsContent);
      break;
    }
  }

  row.appendChild(title);
  row.appendChild(message);
  row.appendChild(details);
  rowWrapper.appendChild(row);

  return { rowWrapper, row };
}

async function doMain() {
  let { config, reports, uptimeMs } = await loadData();

  const cutoffFilter = location.search.split(/[&?]/).find(x => x.startsWith(`cutoffSec=`))
  if (cutoffFilter) {
    const cutoffMs = 1000 * (+cutoffFilter.slice('cutoffSec='.length));
    reports = reports.filter(r => r.discoveryTimeMs <= cutoffMs);
    uptimeMs = cutoffMs;
  }


  document.body.innerHTML = '';

  const h1 = document.createElement('h1');
  const formatTime = millis => {
    const second = 1000;
    const minute = 60 * second;
    const hour = 60 * minute;
    const day = 24 * hour;

    const numDays = (millis / day)|0;
    millis %= day;
    const numHours = (millis / hour)|0;
    millis %= hour;
    const numMinute = (millis / minute)|0;
    millis %= minute;
    const numSecond = (millis / second)|0;
    return [
      numDays ? `${numDays}d` : '',
      (numDays || numHours) ? `${numHours}h` : '',
      (numDays || numHours || numMinute) ? `${numMinute}m` : '',
      `${numSecond}s`,
    ].filter(Boolean).join('');
  }
  let numUnique = 0;
  document.body.appendChild(h1);

  if (config) {
    const details = document.createElement('details');
    details.classList.add('search-config');
    const summary = document.createElement('summary');
    summary.innerText = 'Show search configuration'
    details.appendChild(summary);

    const contents = document.createElement('pre');
    contents.innerText = JSON.stringify(config, null, 2);
    details.appendChild(contents);
    document.body.appendChild(details);
  }

  const filter = document.createElement('input');
  filter.placeholder = 'Search';
  filter.classList.add('filter');
  filter.type = 'text';
  document.body.appendChild(filter);

  let hasMarkedSearchResulAsDivider = false;
  const rowFilterCallbacks = [];
  console.log('adding change list')
  filter.addEventListener('input', () => {
    hasMarkedSearchResulAsDivider = false;
    const val = filter.value;
    rowFilterCallbacks.forEach(cb => cb(val));
  });
  filter.focus({ preventScroll: true });

  const rowList = document.createElement('div');
  rowList.classList.add('rowList');

  const duplicateReceivers = {};

  reports.sort((a, b) => {
    const typeCmp = a.type.localeCompare(b.type);
    if (typeCmp !== 0) { return typeCmp; }
    const aIntemermediates = !!a.details.intermediateSteps;
    const bIntermediates = !!b.details.intermediateSteps;
    if (aIntemermediates != bIntermediates) {
      // Prioritize showing reports with intermediate steps
      return aIntemermediates ? -1 : 1;
    }
    return 0;
  });
  reports.forEach(entry => {
    if (duplicateReceivers[entry.message]) {
      // Same~ish report found before
      duplicateReceivers[entry.message](entry);
      return;
    }

    ++numUnique;
    const { rowWrapper, row } = createRow(entry, config);
    const filterContents = [entry];
    let filterContentsStr = null;
    let duplicatesDetails = null;
    let duplicatesSummary = null;
    // const filterContents = `${entry.type}${entry.message}${JSON.stringify(entry.details)}`.toLowerCase();

    rowFilterCallbacks.push(filter => {
      if (filterContentsStr === null) {
        filterContentsStr = filterContents.map(ent => `${ent.type}${ent.message}${JSON.stringify(ent.details)}`.toLowerCase()).join('\n');
      }
      if (filterContentsStr.includes(filter.toLowerCase())) {
        rowWrapper.setAttribute('data-search', '1')
        rowWrapper.setAttribute('data-divider', '0')
      } else {
        rowWrapper.setAttribute('data-search', '2')
        if (!hasMarkedSearchResulAsDivider) {
          hasMarkedSearchResulAsDivider = true;
          rowWrapper.setAttribute('data-divider', '1')
        } else {
          rowWrapper.setAttribute('data-divider', '0')
        }
      }
    });

    const detailsOpenListener = () => {
      if (duplicatesDetails?.open) {
        duplicatesDetails.removeEventListener('toggle', detailsOpenListener);
        pendingDuplicateAppends.forEach(other => {
          duplicatesDetails.appendChild(createRow(other, config).rowWrapper);
        });
      }
    }
    const pendingDuplicateAppends = [];
    duplicateReceivers[entry.message] = (other) => {
      if (!duplicatesDetails) {
        // First
        duplicatesDetails = document.createElement('details');
        duplicatesSummary = document.createElement('summary');
        duplicatesDetails.appendChild(duplicatesSummary);
        row.appendChild(duplicatesDetails);
        duplicatesDetails.addEventListener("toggle", detailsOpenListener);
      }
      filterContents.push(other);
      filterContentsStr = null;

      duplicatesSummary.innerText = `${filterContents.length - 1} duplicate${filterContents.length >= 3 ? 's' : ''}`;

      pendingDuplicateAppends.push(other);
    }

    rowList.appendChild(rowWrapper);
  });
  document.body.appendChild(rowList);

  h1.innerText = `${numUnique} issues (+${reports.length - numUnique} duplicates)${uptimeMs ? `, found in ${formatTime(uptimeMs)}` : ''}`;
}

function initReportVisualizer() {
  console.log('init!');

  doMain()
    .then(() => console.log('Loaded'))
    .catch(err => {
      console.warn('Error when loading:', err);
      document.body.innerText = `Failed initializing: ${err}`;
    });
}
