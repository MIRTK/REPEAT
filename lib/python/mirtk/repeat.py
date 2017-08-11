"""Auxiliary functions for Python notebooks/scripts used to analyze and compare the results."""

import collections
import numpy as np
import os
import pandas as pd
import re


topdir = os.path.normpath(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..')))


def is_iterable(var):
    """Check if variable is iterable, but not a string."""
    return isinstance(var, collections.Iterable) and not isinstance(var, str)


def is_overlap_measure(measure):
    """Whether given evaluation criterion is a segmentation overlap measure."""
    return measure in ['dsc', 'jsc']


def split_version(regid):
    """Split "regid" column entry into registration software ID and version string."""
    m = re.match(r'^(.+)-(rev_[0-9a-f]+|[0-9]+(\.[0-9]+)?(\.[0-9]+)?|dev|develop|master|latest)$', regid)
    if m is not None:
        return (m.group(1), m.group(2))
    return (regid, None)


def split_regid(regid):
    """Split "regid" column entry into registration toolkit ID, command ID, and version string."""
    regid, version = split_version(regid)
    parts = regid.split('-', 1)
    if len(parts) == 1:
        return (parts[0], None, version)
    else:
        return (parts[0], parts[1], version)


def get_regid(toolkit, command=None, version=None):
    """Create "regid" from individual parts, i.e., toolkit ID, command ID, and toolkit version string."""
    regid = toolkit
    if command:
        regid = regid + '-' + command
    if version:
        regid = regid + '-' + version
    return regid


def cfgidstr(cfgid):
    """Convert cfgid to string."""
    if not isinstance(cfgid, str):
        if isinstance(cfgid, int) or np.issubdtype(cfgid, np.integer):
            cfgid = '{:04d}'.format(cfgid)
        else:
            raise ValueError("cfgid must be int, np.integer, or str")
    return cfgid


def get_csvdir(dataset, regid, cfgid=None):
    """Get absolute path of result CSV files."""
    csvdir = os.path.join(topdir, 'var', 'table', dataset, regid)
    if cfgid:
        csvdir = os.path.join(csvdir, cfgidst(cfgid))
    return csvdir


def get_cfgids(dataset, regid):
    """Get list of IDs of registration parameter sets."""
    re_cfgid = re.compile(r'^[0-9]+$')
    cfgids = [int(d) for d in os.listdir(get_csvdir(dataset, regid)) if re_cfgid.match(d)]
    cfgids.sort()
    return cfgids


def get_tgtids(dataset, regid, cfgid=None):
    """Get list of target image IDs."""
    tgtids = set()
    re_tgtid = re.compile(r'^(.+)-[a-zA-Z0-9]+\.csv$')
    csvdir = get_csvdir(dataset, regid, cfgid=cfgid)
    for d in os.listdir(csvdir):
        m = re_tgtid.match(d)
        if m:
            tgtids.add(m.group(1))
    tgtids = list(tgtids)
    tgtids.sort()
    return tgtids


def get_params(dataset, regid=None, toolkit=None, command=None, version=None, cfgid=None):
    """Get table of registration parameter sets."""
    if not dataset:
        raise ValueError("Need to specify at least one evaluation 'dataset'")
    if not regid and not toolkit:
        raise ValueError("Either 'regid' or at least 'toolkit' must be specified")
    df = pd.DataFrame()
    # recursion for iterable arguments
    if is_iterable(dataset):
        for arg in dataset:
            df = pd.concat([df, get_params(dataset=arg, regid=regid, toolkit=toolkit, command=command, version=version, cfgid=cfgid)])
        return df
    if isinstance(regid, dict):
        toolkit = list(regid.keys())
        command = {}
        version = {}
        for tk in toolkit:
            command[tk] = {}
            if isinstance(regid[tk], dict):
                command[tk] = list(regid[tk].keys())
                for cmd in regid[tk]:
                    k = get_regid(toolkit=tk, command=cmd)
                    version[k] = regid[tk][cmd]
            else:
                command[tk] = regid[tk]
        regid = None
    if is_iterable(regid):
        for arg in regid:
            df = pd.concat([df, get_params(dataset=dataset, regid=arg, toolkit=toolkit, command=command, version=version, cfgid=cfgid)])
        return df
    if is_iterable(toolkit):
        for arg in toolkit:
            df = pd.concat([df, get_params(dataset=dataset, regid=regid, toolkit=arg, command=command, version=version, cfgid=cfgid)])
        return df
    if isinstance(command, dict):
        command = command[toolkit]
    if is_iterable(command):
        for arg in command:
            df = pd.concat([df, get_params(dataset=dataset, regid=regid, toolkit=toolkit, command=arg, version=version, cfgid=cfgid)])
        return df
    if isinstance(version, dict):
        version = version[get_regid(toolkit=toolkit, command=command)]
    if is_iterable(version):
        for arg in version:
            df = pd.concat([df, get_params(dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=arg, cfgid=cfgid)])
        return df
    # all arguments are non-iterable
    if regid:
        toolkit, command, version = split_regid(regid)
    else:
        regid = get_regid(toolkit=toolkit, command=command, version=version)
    parcsv = None
    for name in (regid, get_regid(toolkit=toolkit, command=command, version=None)):
        for subdir in (dataset, ''):
            pardir = os.path.join(topdir, "etc", "params")
            if subdir:
                pardir = os.path.join(pardir, subdir)
            parcsv = os.path.join(pardir, regid + ".csv")
            if os.path.exists(parcsv):
                break
            parcsv = None
        if parcsv is not None:
            break
    if isinstance(cfgid, dict):
        cfgid = cfgid.get(regid, None)
    if parcsv is None:
        df = pd.DataFrame({'cfgid': pd.Series([1])})
    else:
        df = pd.read_csv(parcsv)
        if is_iterable(cfgid):
            df = df[df.cfgid.isin([int(i) for i in cfgid])].copy()
        elif cfgid is not None:
            df = df[df.cfgid==int(cfgid)].copy()
    df.insert(0, 'dataset', dataset)
    df.insert(1, 'regid', regid)
    df.insert(2, 'toolkit', toolkit)
    df.insert(3, 'command', command)
    df.insert(4, 'version', version)
    return df


def read_average_measures(dataset, regid=None, toolkit=None, command=None, version=None, tgtid=None, cfgid=None):
    """Auxiliary function to read output tables of MIRTK 'average-measures' command."""
    if not dataset:
        raise ValueError("Need to specify at least one evaluation 'dataset'")
    if not regid and not toolkit:
        raise ValueError("Either 'regid' or at least 'toolkit' must be specified")
    df = pd.DataFrame()
    # recursion for iterable arguments
    if is_iterable(dataset):
        for arg in dataset:
            df = pd.concat([df, read_average_measures(dataset=arg, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(regid, dict):
        toolkit = list(regid.keys())
        command = {}
        version = {}
        for tk in toolkit:
            command[tk] = {}
            if isinstance(regid[tk], dict):
                command[tk] = list(regid[tk].keys())
                for cmd in regid[tk]:
                    k = get_regid(toolkit=tk, command=cmd)
                    version[k] = regid[tk][cmd]
            else:
                command[tk] = regid[tk]
        regid = None
    if is_iterable(regid):
        for arg in regid:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=arg, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if is_iterable(toolkit):
        for arg in toolkit:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=regid, toolkit=arg, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(command, dict):
        command = command[toolkit]
    if is_iterable(command):
        for arg in command:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=regid, toolkit=toolkit, command=arg, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(version, dict):
        version = version[get_regid(toolkit=toolkit, command=command)]
    if is_iterable(version):
        for arg in version:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=arg, tgtid=tgtid, cfgid=cfgid)])
        return df
    if is_iterable(cfgid):
        for arg in cfgid:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=arg)])
        return df
    # all but tgtid non-iterable
    if not tgtid:
        tgtid = get_tgtids(dataset, regid, cfgid)
    if is_iterable(tgtid):
        for arg in tgtid:
            df = pd.concat([df, read_average_measures(dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=arg, cfgid=cfgid)])
        return df
    # all arguments are non-iterable
    if regid:
        toolkit, command, version = split_regid(regid)
    else:
        regid = get_regid(toolkit=toolkit, command=command, version=version)
    csv_prefix = os.path.join(get_csvdir(dataset, regid, cfgid=cfgid), tgtid)
    if os.path.isfile(csv_prefix + '-mean.csv'):
        dm = pd.read_csv(csv_prefix + '-mean.csv', header=0)
        ds = pd.read_csv(csv_prefix + '-sdev.csv', header=0)
        dn = pd.read_csv(csv_prefix + '-size.csv', header=0)
        df = pd.merge(dm, ds, how='inner', on='roi', suffixes=('_mean', '_sdev'), copy=False)
        df = pd.merge(df, dn, how='inner', on='roi', suffixes=('_mean', '_sdev'), copy=False)
        # insert columns in reverse order
        df.insert(0, 'tgtid', tgtid)
        if cfgid:
            df.insert(0, 'cfgid', int(cfgid))
        df.insert(0, 'version', version)
        df.insert(0, 'command', command)
        df.insert(0, 'toolkit', toolkit)
        df.insert(0, 'regid', regid)
        df.insert(0, 'dataset', dataset)  
    return df


def read_measurements(measure, dataset, regid=None, toolkit=None, command=None, version=None, tgtid=None, cfgid=None):
    """Read pairwise registration measurements."""
    if not measure:
        raise ValueError("Need to specify at least one evaluation 'measure'")
    if not dataset:
        raise ValueError("Need to specify at least one evaluation 'dataset'")
    if not regid and not toolkit:
        raise ValueError("Either 'regid' or at least 'toolkit' must be specified")
    df = pd.DataFrame()
    # recursion for iterable arguments
    if is_iterable(measure):
        for arg in measure:
            df = pd.concat([df, read_measurements(measure=arg, dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if is_iterable(dataset):
        for arg in dataset:
            df = pd.concat([df, read_measurements(measure=measure, dataset=arg, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(regid, dict):
        toolkit = list(regid.keys())
        command = {}
        version = {}
        for tk in toolkit:
            command[tk] = {}
            if isinstance(regid[tk], dict):
                command[tk] = list(regid[tk].keys())
                for cmd in regid[tk]:
                    k = get_regid(toolkit=tk, command=cmd)
                    version[k] = regid[tk][cmd]
            else:
                command[tk] = regid[tk]
        regid = None
    if is_iterable(regid):
        for arg in regid:
            df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=arg, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if is_iterable(toolkit):
        for arg in toolkit:
            df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=regid, toolkit=arg, command=command, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(command, dict):
        command = command[toolkit]
    if is_iterable(command):
        for arg in command:
            df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=regid, toolkit=toolkit, command=arg, version=version, tgtid=tgtid, cfgid=cfgid)])
        return df
    if isinstance(version, dict):
        version = version[get_regid(toolkit=toolkit, command=command)]
    if is_iterable(version):
        for arg in version:
            df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=arg, tgtid=tgtid, cfgid=cfgid)])
        return df
    if is_iterable(cfgid):
        if len(cfgid) == 0:
            cfgid = None
        else:
            for arg in cfgid:
                df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=tgtid, cfgid=arg)])
            return df
    # all but tgtid non-iterable
    if not tgtid:
        tgtid = get_tgtids(dataset, regid, cfgid)
    if is_iterable(tgtid):
        for arg in tgtid:
            df = pd.concat([df, read_measurements(measure=measure, dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=version, tgtid=arg, cfgid=cfgid)])
        return df
    # all arguments are non-iterable
    if regid:
        toolkit, command, version = split_regid(regid)
    else:
        regid = get_regid(toolkit=toolkit, command=command, version=version)
    csv_path = os.path.join(get_csvdir(dataset, regid, cfgid=cfgid), tgtid + '-' + measure + '.csv')
    if os.path.isfile(csv_path):
        df = pd.read_csv(csv_path, header=0, dtype={'srcid': str})
        if measure == 'time':
            replacements = {}
            if 'cpu_time' in df:
                replacements['cpu_time'] = 'user'
            if 'wall_time' in df:
                replacements['wall_time'] = 'real'
            if replacements:
                df.rename(columns=replacements, inplace=True)
        # insert columns in reverse order
        df.insert(0, 'tgtid', tgtid)
        if cfgid:
            df.insert(0, 'cfgid', int(cfgid))
        df.insert(0, 'version', version)
        df.insert(0, 'command', command)
        df.insert(0, 'toolkit', toolkit)
        df.insert(0, 'regid', regid)
        df.insert(0, 'dataset', dataset)   
    return df


def read_results(dataset, regid=None, toolkit=None, command=None, version=None, measure=['vox', 'dsc', 'jac', 'time'], cfgid=None):
    """Read all results for a number of quality measures."""
    if not measure:
        raise ValueError("Need to specify at least one evaluation 'measure'")
    if not dataset:
        raise ValueError("Need to specify at least one evaluation 'dataset'")
    if not regid and not toolkit:
        raise ValueError("Either 'regid' or at least 'toolkit' must be specified")
    dfs = {}
    # recursion for iterable arguments
    if is_iterable(dataset):
        for arg in dataset:
            res = read_results(dataset=arg, regid=regid, toolkit=toolkit, command=command, version=version, measure=measure)
            for m in res:
                if m in dfs:
                    dfs[m] = pd.concat([dfs[m], res[m]])
                else:
                    dfs[m] = res[m]
        return dfs
    if isinstance(regid, dict):
        toolkit = list(regid.keys())
        command = {}
        version = {}
        for tk in toolkit:
            command[tk] = {}
            if isinstance(regid[tk], dict):
                command[tk] = list(regid[tk].keys())
                for cmd in regid[tk]:
                    k = get_regid(toolkit=tk, command=cmd)
                    version[k] = regid[tk][cmd]
            else:
                command[tk] = regid[tk]
        regid = None
    if is_iterable(regid):
        for arg in regid:
            res = read_results(dataset=dataset, regid=arg, toolkit=toolkit, command=command, version=version, measure=measure)
            for m in res:
                if m in dfs:
                    dfs[m] = pd.concat([dfs[m], res[m]])
                else:
                    dfs[m] = res[m]
        return dfs
    if is_iterable(toolkit):
        for arg in toolkit:
            res = read_results(dataset=dataset, regid=regid, toolkit=arg, command=command, version=version, measure=measure)
            for m in res:
                if m in dfs:
                    dfs[m] = pd.concat([dfs[m], res[m]])
                else:
                    dfs[m] = res[m]
        return dfs
    if isinstance(command, dict):
        command = command[toolkit]
    if is_iterable(command):
        for arg in command:
            res = read_results(dataset=dataset, regid=regid, toolkit=toolkit, command=arg, version=version, measure=measure)
            for m in res:
                if m in dfs:
                    dfs[m] = pd.concat([dfs[m], res[m]])
                else:
                    dfs[m] = res[m]
        return dfs
    if isinstance(version, dict):
        version = version[get_regid(toolkit=toolkit, command=command)]
    if is_iterable(version):
        for arg in version:
            res = read_results(dataset=dataset, regid=regid, toolkit=toolkit, command=command, version=arg, measure=measure)
            for m in res:
                if m in dfs:
                    dfs[m] = pd.concat([dfs[m], res[m]])
                else:
                    dfs[m] = res[m]
        return dfs
    if not is_iterable(measure):
        measure = [measure]
    # all arguments are non-iterable, except for 'measure'
    if not regid:
        regid = get_regid(toolkit=toolkit, command=command, version=version)
    if regid == 'affine':
        cfgids = []
    elif cfgid is not None:
        if isinstance(cfgid, dict):
            cfgids = cfgid[regid]
            if isinstance(cfgids, dict):
                cfgids = cfgids[dataset]
        elif is_iterable(cfgid):
            cfgids = list(cfgid)
        else:
            cfgids = [cfgid]
    else:
        cfgids = []
        for cfgid in get_params(dataset, regid).cfgid:
            if os.path.isdir(get_csvdir(dataset, regid, cfgid)):
                cfgids.append(cfgid)
    for m in measure:
        if m == 'vox':
            df = read_average_measures(dataset=dataset, regid=regid, cfgid=cfgids)
        elif m == 'jac':
            df = read_measurements(measure='logjac', dataset=dataset, regid=regid, cfgid=cfgids)
            if 'nexcl' in df and 'n' in df and 'pctexcl' not in df:
                df = df.assign(pctexcl=(100. * df.nexcl / (df.n + df.nexcl)))
        else:
            df = read_measurements(measure=m, dataset=dataset, regid=regid, cfgid=cfgids)
            if m == 'dsc':
                if 'srcid' in df:
                    df.srcid = df.srcid.map(lambda x: x.split('-')[0])
                id_vars = df.columns.intersection(['dataset', 'regid', 'toolkit', 'command', 'version', 'cfgid', 'tgtid', 'srcid']).tolist()
                df = pd.melt(df, id_vars=id_vars, var_name='label', value_name='dsc')
        if 'tgtid' in df and 'srcid' in df:
            df = df[df.tgtid!=df.srcid]
        dfs[m] = df.copy()
    return dfs


def average_overlap(dfs, measure=None):
    """Compute average overlap of those labels within a label group, and those that are not."""
    avg = {}
    if measure is None and isinstance(dfs, pd.DataFrame):
        dfs = dict(dsc=dfs)
        measure = ['dsc']
    elif not is_iterable(measure):
        if not isinstance(dfs, dict):
            dfs = {measure: dfs}
        measure = [measure]
    for m in dfs:
        if m in measure or is_overlap_measure(m):
            reg_info = dfs[m][['regid', 'toolkit', 'command', 'version']].drop_duplicates()
            df = dfs[m].assign(ingroup=False)
            datasets = df.dataset.unique()
            for dataset in datasets:
                csv_path = os.path.join(topdir, 'etc', 'dataset', dataset + '.csv')
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
            id_vars = df.columns.intersection(['dataset', 'regid', 'cfgid', 'tgtid', 'srcid', 'ingroup']).tolist()
            g = df.groupby(id_vars)
            df = g.mean()  #.assign(count=g.size())
            df.reset_index(drop=False, inplace=True)
            avg[m] = df[df.ingroup==True].merge(reg_info, on=['regid'])
            if 'ingroup' in avg[m]:
                del avg[m]['ingroup']
    if len(measure) == 1 and measure[0] is not None:
        return avg[measure[0]]
    return avg


def average_group_overlap(dfs, measure=None):
    """Compute average overlap of labels within each defined label group."""
    avg = {}
    if measure is None and isinstance(dfs, pd.DataFrame):
        dfs = dict(dsc=dfs)
        measure = ['dsc']
    elif not is_iterable(measure):
        if not isinstance(dfs, dict):
            dfs = {measure: dfs}
        measure = [measure]
    for m in dfs:
        if m in measure or is_overlap_measure(m):
            reg_info = dfs[m][['regid', 'toolkit', 'command', 'version']].drop_duplicates()
            df = dfs[m].assign(group=None)
            datasets = df.dataset.unique()
            for dataset in datasets:
                csv_path = os.path.join(topdir, 'etc', 'dataset', dataset + '.csv')
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
                    df.loc[df.dataset==dataset, 'group'] = col
            id_vars = df.columns.intersection(['dataset', 'regid', 'cfgid', 'tgtid', 'srcid', 'group']).tolist()
            g = df.groupby(id_vars)
            df = g.mean()  #.assign(count=g.size())
            df.reset_index(drop=False, inplace=True)
            avg[m] = df.merge(reg_info, on=['regid'])
    if len(measure) == 1 and not measure[0] is None:
        return avg[measure[0]]
    return avg


def set_params(df, params=None):
    """Merge DataFrame with registration parameters obtained with get_params."""
    if isinstance(df, pd.DataFrame):
        id_vars = df.columns.intersection(['dataset', 'regid', 'cfgid']).tolist()
        datasets = df.dataset.unique().tolist()
        regids = df.regid.unique().tolist()
        result = pd.DataFrame()
        for regid in regids:
            for dataset in datasets:
                if isinstance(params, pd.DataFrame):
                    par = params[(params.dataset==dataset)&(params.regid==regid)]
                elif isinstance(params, dict):
                    par = params[regid]
                    if isinstance(par, dict):
                        par = par[dataset]
                else:
                    par = get_params(dataset, regid)
                par.set_index(id_vars, inplace=True)
                col = par.columns.difference(df.columns)
                result = pd.concat([result, pd.merge(df, par[col], left_on=id_vars, right_index=True, how='inner').reset_index(drop=True)])
        return result
    elif isinstance(df, dict):
        for m in df:
            df[m] = set_params(df[m])
        return df
    elif is_iterable(df):
        out = []
        for table in df:
            out.append(set_params(table))
        return out
    else:
        raise ValueError("Argument must be pandas.DataFrame, dict, or iterable")



#### TODO

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
