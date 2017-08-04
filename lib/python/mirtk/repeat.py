"""Auxiliary functions for Python notebooks/scripts used to analyze and compare the results."""

import re
import os
import pandas as pd


topdir = os.path.normpath(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..')))


def is_overlap_measure(measure):
    return measure in ['dsc', 'jsc']


def get_registration_tables_dir(dataset, regid):
    return os.path.join(topdir, 'var', 'table', dataset, regid)


def get_cfgids(dataset, regid):
    """Get list of IDs of registration parameter sets."""
    if regid == 'affine':
        return [None]
    re_cfgid = re.compile(r'^[0-9]+$')
    cfgids = [d for d in os.listdir(get_registration_tables_dir(dataset, regid)) if re_cfgid.match(d)]
    cfgids.sort()
    return cfgids


def get_tgtids(dataset, regid, cfgid=None):
    """Get list of target image IDs."""
    tgtids = set()
    re_tgtid = re.compile(r'^(.+)-[a-zA-Z0-9]+\.csv$')
    regdir = get_registration_tables_dir(dataset, regid)
    if cfgid:
        regdir = os.path.join(regdir, cfgid)
    for d in os.listdir(regdir):
        m = re_tgtid.match(d)
        if m:
            tgtids.add(m.group(1))
    tgtids = list(tgtids)
    tgtids.sort()
    return tgtids


def get_srcids(dataset, regid, cfgid, tgtid):
    """Get list of source image IDs."""
    if regid == 'affine':
        return get_tgtids(dataset, regid)
    df = read_measurements('time', dataset, regid, tgtid, cfgid)
    if df is None:
        raise Exception("get_srcids: Missing 'time' table for cfgid={} and tgtid={}".format(cfgid, tgtid))
    srcids = df.srcid.tolist()
    srcids.sort()
    if len(srcids) == 0:
        raise Exception("Could not determine srcids: {}".format(df.srcid))
    return srcids


def get_params(dataset, regid, version=None):
    """Get table of registration parameter sets."""
    if version:
        parcsv = os.path.join(topdir, "etc", "params", dataset, "{regid}-{ver}.csv".format(regid=regid, ver=version))
    if not version or not os.path.isfile(parcsv):
        parcsv = os.path.join(topdir, "etc", "params", dataset, "{regid}.csv".format(regid=regid))
    if version and not os.path.isfile(parcsv):
        parcsv = os.path.join(topdir, "etc", "params", "{regid}-{ver}.csv".format(regid=regid, ver=version))    
    if not os.path.isfile(parcsv):
        parcsv = os.path.join(topdir, "etc", "params", "{regid}.csv".format(regid=regid))
    df = pd.read_csv(parcsv)
    df.insert(0, 'dataset', dataset)
    return df


def read_label_volumes(dataset, regid, tgtid, cfgid=None):
    """Get volumes of segmentation labels."""
    unit = np.prod(spacing['alberts'])
    prefix = get_registration_tables_dir(dataset, regid)
    if cfgid:
        prefix = os.path.join(prefix, cfgid)
    prefix = os.path.join(prefix, tgtid)
    df = pd.read_csv(prefix + "-size.csv", header=0)
    df = pd.DataFrame(df[df.roi.str.startswith("seg=")])
    df.loc[:,'roi'] = df.roi.map(lambda s: int(s.split('=')[1]))
    df.rename(columns=dict(roi='label'), inplace=True)
    df = df.assign(vol=df.n*unit)
    df.insert(1, 'dataset', dataset)
    df.insert(2, 'regid', regid)
    df.insert(3, 'cfgid', int(cfgid))
    df.insert(4, 'tgtid', tgtid)
    return df.set_index('label')


def read_average_measures(dataset, regid, tgtid, cfgid=None):
    """Auxiliary function to read output tables of MIRTK 'average-measures' command."""
    prefix = get_registration_tables_dir(dataset, regid)
    if cfgid:
        prefix = os.path.join(prefix, cfgid)
    prefix = os.path.join(prefix, tgtid)
    if not os.path.isfile(prefix + "-mean.csv"):
        return None
    m = pd.read_csv(prefix + "-mean.csv", header=0)
    s = pd.read_csv(prefix + "-sdev.csv", header=0)
    n = pd.read_csv(prefix + "-size.csv", header=0)
    d = pd.merge(m, s, how='inner', on='roi', suffixes=('_mean', '_sdev'), copy=False)
    d = pd.merge(d, n, how='inner', on='roi', suffixes=('_mean', '_sdev'), copy=False)
    c = 0
    d.insert(c, 'dataset', dataset)
    c += 1
    d.insert(c, 'regid', regid)
    c += 1
    if cfgid:
        d.insert(c, 'cfgid', int(cfgid))
        c += 1
    d.insert(c, 'tgtid', tgtid)
    return d


def read_measurements(measure, dataset, regid, tgtid, cfgid=None, srcids=None):
    """Read pairwise registration measurements."""
    prefix = get_registration_tables_dir(dataset, regid)
    if cfgid:
        prefix = os.path.join(prefix, cfgid)
    prefix = os.path.join(prefix, tgtid)
    table = prefix + "-" + measure + ".csv"
    if os.path.isfile(table):
        d = pd.read_csv(table, header=0,dtype={'srcid': str})
        if measure == 'time' and not regid.startswith('mirtk-'):
            d.rename(columns=dict(user='cpu_time', real='wall_time'), inplace=True)
        c = 0
        d.insert(c, 'dataset', dataset)
        c += 1
        d.insert(c, 'regid', regid)
        c += 1
        if cfgid:
            d.insert(c, 'cfgid', int(cfgid))
            c += 1
        d.insert(c, 'tgtid', tgtid)
        c += 1
        if srcids and 'srcid' not in d:
            d.insert(c, 'srcid', list(srcids))
            c += 1
        return d
    else:
        return None


def read_results(dataset, regid, version=None, measures=['vox', 'dsc', 'jac', 'time']):
    """Read all results for a given registration method evaluated on a given dataset."""
    dfs = {}
    if regid == 'affine':
        tgtids = get_tgtids(dataset, regid)
        for measure in ['dsc']:
            dfs[measure] = pd.DataFrame()
            for tgtid in tgtids:
                df = read_measurements(measure, dataset=dataset, regid=regid, tgtid=tgtid)
                if df is None:
                    print("WARNING: Missing {m} for target image {t}".format(m=measure, t=tgtid))
                else:
                    dfs[measure] = pd.concat([dfs[measure], df])
        if 'dsc' in dfs:
            id_vars = ['dataset', 'regid', 'tgtid']
            if 'srcid' in dfs['dsc']:
                id_vars.append('srcid')
            dfs['dsc'] = pd.melt(dfs['dsc'], id_vars=id_vars, var_name='label', value_name='dsc')
        return dfs
    else:
        params = get_params(dataset, regid, version=version)
        params.drop_duplicates(subset=[c for c in params.columns if c != 'cfgid'], inplace=True)
        if version:
            regid = "{}-{}".format(regid, version)
        cfgids = ['{0:04d}'.format(i) for i in params.cfgid]
        tgtids = get_tgtids(dataset, regid, cfgids[0])
        for measure in measures:
            dfs[measure] = pd.DataFrame()
            for cfgid in cfgids:
                for tgtid in tgtids:
                    if measure == 'vox':
                        df = read_average_measures(dataset=dataset, regid=regid, tgtid=tgtid, cfgid=cfgid)
                        if df is None:
                            print("WARNING: Missing average voxel-wise measures for target image {t} and parameter set {c}".format(t=tgtid, c=cfgid))
                        else:
                            dfs[measure] = pd.concat([dfs[measure], df])
                    else:
                        if measure == 'jac':
                            table_suffix = 'logjac'
                        else:
                            table_suffix = measure
                        df = read_measurements(table_suffix, dataset=dataset, regid=regid, tgtid=tgtid, cfgid=cfgid)
                        if df is None:
                            print("WARNING: Missing {m} for target image {t} and parameter set {c}".format(m=measure, t=tgtid, c=cfgid))
                        else:
                            dfs[measure] = pd.concat([dfs[measure], df])
        if 'dsc' in dfs:
            dfs['dsc'].srcid = dfs['dsc'].srcid.map(lambda x: x.split('-')[0])
            dfs['dsc'] = pd.melt(dfs['dsc'], id_vars=['dataset', 'regid', 'cfgid', 'tgtid', 'srcid'], var_name='label', value_name='dsc')
        if 'jac' in dfs:
            dfs['jac'] = dfs['jac'].assign(pctexcl=(100. * dfs['jac'].nexcl / (dfs['jac'].n + dfs['jac'].nexcl)))
        for measure in measures:
            dfs[measure] = pd.merge(dfs[measure], params, on=['dataset', 'cfgid'], how='left')
            dfs[measure].reset_index(drop=True, inplace=True)
        return dfs


def average_group_overlap(df, measure='dsc'):
    """Compute average overlap of labels within each defined label group."""
    df = df.assign(label_group=None)
    datasets = df.dataset.unique()
    for dataset in datasets:
        csv_path = os.path.join(topdir, 'etc', 'dataset', dataset + '-labels.csv')
        if os.path.isfile(csv_path):
            label2group = {}
            labels = pd.read_csv(csv_path, index_col=0, header=0)
            for column in labels.columns:
                if column.startswith('Label Group: '):
                    group = column[13:]
                    for label in labels[labels[column]=='+'].index.tolist():
                        label2group[int(label)] = group
            sub = df[df.dataset==dataset]
            col = sub.label.map(lambda l: label2group[int(l)] if int(l) in label2group else None)
            df.loc[df.dataset==dataset, 'label_group'] = col
    id_vars = ['dataset', 'regid']
    if 'cfgid' in df:
        id_vars.append('cfgid')
    id_vars.extend(['tgtid', 'srcid', 'label_group'])
    df = df.groupby(id_vars).mean()
    df.reset_index(drop=False, inplace=True)
    return df


def average_overlap(df, measure='dsc'):
    """Compute average overlap of those labels within a label group, and those that are not."""
    df = df.assign(ingroup=False)
    datasets = df.dataset.unique()
    for dataset in datasets:
        csv_path = os.path.join(topdir, 'etc', 'dataset', dataset + '-labels.csv')
        if os.path.isfile(csv_path):
            label2group = {}
            labels = pd.read_csv(csv_path, index_col=0, header=0)
            for column in labels.columns:
                if column.startswith('Label Group: '):
                    group = column[13:]
                    for label in labels[labels[column]=='+'].index.tolist():
                        label2group[int(label)] = group
            sub = df[df.dataset==dataset]
            col = sub.label.map(lambda l: int(l) in label2group)
            df.loc[df.dataset==dataset, 'ingroup'] = col
    id_vars = ['dataset', 'regid']
    if 'cfgid' in df:
        id_vars.append('cfgid')
    id_vars.extend(['tgtid', 'srcid', 'ingroup'])
    df = df.groupby(id_vars).mean()
    df.reset_index(drop=False, inplace=True)
    return df
